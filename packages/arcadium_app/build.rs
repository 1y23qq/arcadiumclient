fn main() {
    #[cfg(windows)]
    {
        let mut res = winresource::WindowsResource::new();
        res.set_icon("icons/icon.ico");

        let version = env!("CARGO_PKG_VERSION");
        res.set("CompanyName", "0aurorq3");
        res.set("ProductName", "Arcadium Client");
        res.set("FileDescription", "Arcadium Client");
        res.set("LegalCopyright", "© 2026 0aurorq3");
        res.set("OriginalFilename", "Arcadium.exe");
        res.set("InternalName", "arcadium_app");
        res.set("FileVersion", version);
        res.set("ProductVersion", version);

        res.compile()
            .expect("failed to embed Windows icon resource");
    }
}
