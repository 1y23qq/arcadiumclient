import Foundation
import Security

let prefix = Data("ARCADIUM_KEYCHAIN_V1\n".utf8)
guard CommandLine.arguments.count == 2,
      let marker = try? Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1])),
      marker.starts(with: prefix),
      let account = String(data: marker.dropFirst(prefix.count), encoding: .utf8),
      account.count == 64,
      account.utf8.allSatisfy({ (48...57).contains($0) || (97...102).contains($0) }) else {
    print("{\"probe\":\"invalid fixture marker\"}")
    exit(2)
}

let query: [String: Any] = [
    kSecClass as String: kSecClassGenericPassword,
    kSecAttrService as String: "io.github.arcadiumclient.launcher.credentials",
    kSecAttrAccount as String: account,
    kSecReturnData as String: true,
    kSecMatchLimit as String: kSecMatchLimitOne,
    kSecUseAuthenticationUI as String: kSecUseAuthenticationUIFail,
]
var item: CFTypeRef?
let status = SecItemCopyMatching(query as CFDictionary, &item)
var output: [String: Any] = ["probe": "keychain", "status": Int(status)]
if status == errSecSuccess, let data = item as? Data,
   let value = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
   let users = value["users"] as? [String: [String: Any]] {
    let allowed = Set(["ArcadiumFixture", "ArcadiumTestOne", "ArcadiumTestTwo"])
    output["users"] = users.values.map { user -> [String: String] in
        let name = user["username"] as? String ?? ""
        let kind = user["kind"] as? String ?? ""
        return ["fixture": allowed.contains(name) ? name : "unexpected account",
                "kind": ["microsoft", "offline"].contains(kind) ? kind : "unknown"]
    }
}
let encoded = try JSONSerialization.data(withJSONObject: output, options: [.sortedKeys])
print(String(data: encoded, encoding: .utf8)!)
