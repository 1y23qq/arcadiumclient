#include <CoreFoundation/CoreFoundation.h>
#include <Security/Security.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static CFMutableDictionaryRef query(const char *account, int protected) {
    CFMutableDictionaryRef value = CFDictionaryCreateMutable(NULL, 0,
        &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);
    CFStringRef name = CFStringCreateWithCString(NULL, account, kCFStringEncodingUTF8);
    CFDictionarySetValue(value, kSecClass, kSecClassGenericPassword);
    CFDictionarySetValue(value, kSecAttrService, CFSTR("io.github.arcadiumclient.launcher.credentials"));
    CFDictionarySetValue(value, kSecAttrAccount, name);
    if (protected) CFDictionarySetValue(value, kSecUseDataProtectionKeychain, kCFBooleanTrue);
    CFRelease(name);
    return value;
}

static int valid_account(const char *account) {
    if (strlen(account) != 64) return 0;
    for (const char *at = account; *at; at++) {
        if (!((*at >= '0' && *at <= '9') || (*at >= 'a' && *at <= 'f'))) return 0;
    }
    return 1;
}

int main(int argc, char **argv) {
    int protected = argc == 2 && strcmp(argv[1], "protected") == 0;
    char line[256], operation[32], account[65];
    unsigned epoch;
    while (fgets(line, sizeof(line), stdin)) {
        operation[0] = account[0] = 0;
        epoch = 0;
        if (sscanf(line, "%31s %64s %u", operation, account, &epoch) < 1) continue;
        if (strcmp(operation, "QUIT") == 0) return 0;
        if (!valid_account(account)) {
            puts("{\"status\":-50,\"matches\":false}");
            fflush(stdout);
            continue;
        }
        CFMutableDictionaryRef parameters = query(account, protected);
        char bytes[80];
        int length = snprintf(bytes, sizeof(bytes), "arcadium-keychain-fixture:%u", epoch);
        OSStatus status = errSecParam;
        int matches = 0, updated = 0;
        if (strcmp(operation, "SAVE") == 0) {
            CFDataRef data = CFDataCreate(NULL, (const UInt8 *)bytes, length);
            CFDictionarySetValue(parameters, kSecValueData, data);
            CFTypeRef discarded = NULL;
            status = SecItemAdd(parameters, &discarded);
            if (discarded) CFRelease(discarded);
            if (status == errSecDuplicateItem) {
                CFDictionaryRemoveValue(parameters, kSecValueData);
                CFMutableDictionaryRef attributes = CFDictionaryCreateMutable(NULL, 0,
                    &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);
                CFDictionarySetValue(attributes, kSecValueData, data);
                status = SecItemUpdate(parameters, attributes);
                updated = 1;
                CFRelease(attributes);
            }
            CFRelease(data);
        } else if (strcmp(operation, "READ") == 0) {
            CFDictionarySetValue(parameters, kSecReturnData, kCFBooleanTrue);
            CFTypeRef result = NULL;
            status = SecItemCopyMatching(parameters, &result);
            if (status == errSecSuccess && result && CFGetTypeID(result) == CFDataGetTypeID()) {
                matches = CFDataGetLength(result) == length
                    && memcmp(CFDataGetBytePtr(result), bytes, length) == 0;
            }
            if (result) CFRelease(result);
        } else if (strcmp(operation, "DELETE") == 0) {
            status = SecItemDelete(parameters);
        }
        CFRelease(parameters);
        printf("{\"status\":%d,\"matches\":%s,\"updated\":%s}\n",
            (int)status, matches ? "true" : "false", updated ? "true" : "false");
        fflush(stdout);
    }
    return 0;
}
