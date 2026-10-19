use std::sync::mpsc::{self, RecvTimeoutError, Sender};
use std::time::{Duration, Instant};

use discord_rich_presence::activity::{Activity, Assets, Button, Timestamps};
use discord_rich_presence::{DiscordIpc, DiscordIpcClient};

use arcadium_common::constants::DISCORD_CLIENT_ID;

const RECONNECT_BACKOFF: Duration = Duration::from_secs(60);

const IDLE_POLL: Duration = Duration::from_secs(3600);
const CONNECTED_POLL: Duration = Duration::from_secs(30);

const LARGE_IMAGE: &str = "https://cdn.discordapp.com/app-icons/1558477538937016564/042c0895f74e8d02c2dcb6dbcfa9659a.png?size=512";

#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub enum Presence {
    #[default]
    Idle,
    Playing {
        cluster: String,
        mc_version: String,
    },
}

impl Presence {
    fn details(&self) -> &'static str {
        match self {
            Self::Idle => "Idle",
            Self::Playing { .. } => "Playing Minecraft",
        }
    }

    fn state(&self) -> String {
        match self {
            Self::Idle => "In the launcher".to_owned(),
            Self::Playing {
                cluster,
                mc_version,
            } => format!("{cluster} ({mc_version})"),
        }
    }
}

enum Command {
    SetPresence(Presence),
    SetEnabled(bool),
    Shutdown {
        clear: bool,
        finished: Option<Sender<()>>,
    },
}

#[derive(Clone)]
pub struct DiscordRpc {
    tx: Sender<Command>,
}

impl DiscordRpc {
    pub fn spawn(enabled: bool) -> Self {
        Self::spawn_with_presence(enabled, Presence::Idle)
    }

    pub fn spawn_with_presence(enabled: bool, presence: Presence) -> Self {
        let (tx, rx) = mpsc::channel();

        std::thread::Builder::new()
            .name("discord-rpc".to_owned())
            .spawn(move || {
                let mut worker = Worker::new(enabled);
                worker.presence = presence;
                worker.run(&rx);
            })
            .expect("failed to spawn the Discord RPC thread");

        Self { tx }
    }

    pub fn set_presence(&self, presence: Presence) {
        self.send(Command::SetPresence(presence));
    }

    pub fn set_enabled(&self, enabled: bool) {
        self.send(Command::SetEnabled(enabled));
    }

    pub fn shutdown(&self) {
        self.send(Command::Shutdown {
            clear: true,
            finished: None,
        });
    }

    pub fn shutdown_for_handoff(&self) {
        self.shutdown_and_wait(false);
    }

    pub fn shutdown_and_wait(&self, clear: bool) {
        let (tx, rx) = mpsc::channel();
        self.send(Command::Shutdown {
            clear,
            finished: Some(tx),
        });
        let _ = rx.recv_timeout(Duration::from_secs(2));
    }

    fn send(&self, command: Command) {
        if self.tx.send(command).is_err() {
            tracing::debug!("Discord RPC worker is gone; dropping command");
        }
    }
}

struct Worker {
    client: DiscordIpcClient,
    configured: bool,
    enabled: bool,
    connected: bool,
    presence: Presence,
    presence_since: i64,
    next_attempt: Instant,
}

impl Worker {
    fn new(enabled: bool) -> Self {
        let client_id = std::env::var("ARCADIUM_DISCORD_CLIENT_ID")
            .unwrap_or_else(|_| DISCORD_CLIENT_ID.to_owned());
        let client_id = client_id.trim();
        let configured = valid_client_id(client_id);
        if !client_id.is_empty() && !configured {
            tracing::warn!("Discord Application ID must be a numeric snowflake");
        }
        Self {
            client: DiscordIpcClient::new(client_id),
            configured,
            enabled: enabled && configured,
            connected: false,
            presence: Presence::Idle,
            presence_since: chrono::Utc::now().timestamp(),
            next_attempt: Instant::now(),
        }
    }

    fn run(mut self, rx: &mpsc::Receiver<Command>) {
        self.publish();
        let mut clear = true;
        let mut finished = None;

        loop {
            match rx.recv_timeout(self.poll_interval()) {
                Ok(Command::SetPresence(presence)) => self.set_presence(presence),
                Ok(Command::SetEnabled(enabled)) => self.set_enabled(enabled),
                Ok(Command::Shutdown {
                    clear: requested,
                    finished: callback,
                }) => {
                    clear = requested;
                    finished = callback;
                    break;
                }
                Err(RecvTimeoutError::Disconnected) => break,
                Err(RecvTimeoutError::Timeout) => self.publish(),
            }
        }

        if clear {
            self.disconnect();
        } else {
            self.reset_client();
        }
        if let Some(finished) = finished {
            let _ = finished.send(());
        }
    }

    fn poll_interval(&self) -> Duration {
        if !self.enabled {
            return IDLE_POLL;
        }
        if self.connected {
            return CONNECTED_POLL;
        }

        self.next_attempt
            .saturating_duration_since(Instant::now())
            .max(Duration::from_millis(100))
    }

    fn set_presence(&mut self, presence: Presence) {
        if self.presence == presence {
            return;
        }

        self.presence = presence;
        self.presence_since = chrono::Utc::now().timestamp();
        self.publish();
    }

    fn set_enabled(&mut self, enabled: bool) {
        if self.enabled == enabled {
            return;
        }

        self.enabled = enabled && self.configured;

        if enabled {
            self.next_attempt = Instant::now();
            self.publish();
        } else {
            self.disconnect();
        }
    }

    fn publish(&mut self) {
        if !self.enabled || !self.ensure_connected() {
            return;
        }

        let state = self.presence.state();
        let activity = Activity::new()
            .details(self.presence.details())
            .state(&state)
            .timestamps(Timestamps::new().start(self.presence_since))
            .assets(
                Assets::new()
                    .large_image(LARGE_IMAGE)
                    .large_text("Arcadium Client"),
            )
            .buttons(vec![Button::new("Website", "https://arcadiumclient.com/")]);

        let result = self
            .client
            .set_activity(activity)
            .map_err(|err| err.to_string())
            .and_then(|()| Self::receive_activity_result(&mut self.client));
        if let Err(err) = result {
            tracing::warn!("Discord RPC set_activity failed: {err}");
            self.drop_connection();
        }
    }

    fn receive_activity_result(client: &mut impl DiscordIpc) -> Result<(), String> {
        for _ in 0..8 {
            let (opcode, response) = client.recv().map_err(|err| err.to_string())?;
            match opcode {
                3 => client.send(response, 4).map_err(|err| err.to_string())?,
                2 => return Err("Discord closed the IPC connection".to_owned()),
                1 if response["evt"] == "ERROR" => {
                    return Err(format!(
                        "Discord rejected activity (code {})",
                        response["data"]["code"]
                    ));
                }
                1 if response["cmd"] == "SET_ACTIVITY" => return Ok(()),
                _ => {}
            }
        }
        Err("Discord did not acknowledge the activity update".to_owned())
    }

    fn ensure_connected(&mut self) -> bool {
        if self.connected {
            return true;
        }

        if Instant::now() < self.next_attempt {
            return false;
        }
        self.next_attempt = Instant::now() + RECONNECT_BACKOFF;

        self.reset_client();
        let result = self.client.connect();

        match result {
            Ok(()) => {
                tracing::info!("Discord RPC connected");
                self.connected = true;
                true
            }
            Err(err) => {
                tracing::debug!("Discord RPC connect failed (is Discord running?): {err}");
                false
            }
        }
    }

    fn drop_connection(&mut self) {
        self.connected = false;
        self.reset_client();
        self.next_attempt = Instant::now() + RECONNECT_BACKOFF;
    }

    fn reset_client(&mut self) {
        let id = self.client.get_client_id().to_owned();
        self.client = DiscordIpcClient::new(id);
    }

    fn disconnect(&mut self) {
        if !self.connected {
            return;
        }

        let _ = self.client.clear_activity();
        self.connected = false;
        self.reset_client();
    }
}

fn valid_client_id(value: &str) -> bool {
    (17..=20).contains(&value.len())
        && value.bytes().all(|byte| byte.is_ascii_digit())
        && value.parse::<u64>().is_ok_and(|id| id != 0)
}

#[cfg(test)]
mod tests {
    use super::*;
    use discord_rich_presence::error::Error;
    use serde_json::{Value, json};
    use std::collections::VecDeque;

    struct Responses {
        incoming: VecDeque<(u32, Value)>,
        sent: Vec<(u8, Value)>,
    }

    impl DiscordIpc for Responses {
        fn get_client_id(&self) -> &str {
            "1558477538937016564"
        }
        fn connect_ipc(&mut self) -> Result<(), Error> {
            Ok(())
        }
        fn close(&mut self) -> Result<(), Error> {
            Ok(())
        }
        fn read(&mut self, _: &mut [u8]) -> Result<(), Error> {
            panic!("unexpected transport read")
        }
        fn write(&mut self, _: &[u8]) -> Result<(), Error> {
            panic!("unexpected transport write")
        }
        fn recv(&mut self) -> Result<(u32, Value), Error> {
            self.incoming.pop_front().ok_or(Error::NotConnected)
        }
        fn send(&mut self, value: Value, opcode: u8) -> Result<(), Error> {
            self.sent.push((opcode, value));
            Ok(())
        }
    }

    #[test]
    fn responds_to_ping_before_reading_the_activity_acknowledgement() {
        let ping = json!({"ping":"check"});
        let mut replies = Responses {
            incoming: VecDeque::from([
                (3, ping.clone()),
                (1, json!({"cmd":"SET_ACTIVITY","evt":null})),
            ]),
            sent: Vec::new(),
        };
        assert!(Worker::receive_activity_result(&mut replies).is_ok());
        assert_eq!(replies.sent, vec![(4, ping)]);
        assert!(replies.incoming.is_empty());
    }

    #[test]
    fn rejected_or_closed_connections_trigger_recovery() {
        for frame in [
            (1, json!({"evt":"ERROR","data":{"code":4000}})),
            (2, json!({})),
        ] {
            let mut replies = Responses {
                incoming: VecDeque::from([frame]),
                sent: Vec::new(),
            };
            assert!(Worker::receive_activity_result(&mut replies).is_err());
        }
    }

    #[test]
    fn unrelated_events_cannot_keep_the_worker_busy_forever() {
        let mut replies = Responses {
            incoming: std::iter::repeat_n((1, json!({"cmd":"DISPATCH"})), 9).collect(),
            sent: Vec::new(),
        };
        assert!(Worker::receive_activity_result(&mut replies).is_err());
        assert_eq!(replies.incoming.len(), 1);
    }

    #[test]
    fn disconnect_keeps_the_registration_and_resets_the_connection() {
        let mut worker = Worker::new(false);
        let id = worker.client.get_client_id().to_owned();
        worker.connected = true;
        worker.drop_connection();
        assert!(!worker.connected);
        assert_eq!(worker.client.get_client_id(), id);
        worker.connected = true;
        worker.disconnect();
        assert!(!worker.connected);
        assert_eq!(worker.client.get_client_id(), id);
    }

    #[test]
    fn rejects_secrets_placeholders_and_overflow_as_application_ids() {
        assert!(valid_client_id("123456789012345678"));
        for value in [
            "",
            "APPLICATION_ID",
            "bot.token.secret",
            "000000000000000000",
            "18446744073709551616",
        ] {
            assert!(!valid_client_id(value));
        }
    }

    #[test]
    fn reconnect_checks_do_not_reset_the_play_session() {
        let mut worker = Worker::new(false);
        worker.enabled = true;
        worker.connected = true;
        worker.presence = Presence::Playing {
            cluster: "Arcadium".to_owned(),
            mc_version: "1.8.9".to_owned(),
        };
        worker.presence_since = 123;
        let presence = worker.presence.clone();
        worker.set_presence(presence);
        assert_eq!(worker.presence_since, 123);
        assert_eq!(worker.poll_interval(), CONNECTED_POLL);
        worker.connected = false;
        worker.next_attempt = Instant::now() + RECONNECT_BACKOFF;
        assert!(worker.poll_interval() <= RECONNECT_BACKOFF);
        worker.enabled = false;
        assert_eq!(worker.poll_interval(), IDLE_POLL);
    }
}
