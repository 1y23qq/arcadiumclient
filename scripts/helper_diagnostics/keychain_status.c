#include <CoreFoundation/CoreFoundation.h>
#include <Security/Security.h>
#include <stdio.h>
#include <stdlib.h>

static int fixture_query(CFDictionaryRef query) {
    const char *account = getenv("ARCADIUM_DIAGNOSTIC_KEYCHAIN_ACCOUNT");
    if (!account || !query) return 0;
    CFTypeRef service = CFDictionaryGetValue(query, kSecAttrService);
    CFTypeRef stored_account = CFDictionaryGetValue(query, kSecAttrAccount);
    if (!service || !stored_account || CFGetTypeID(service) != CFStringGetTypeID()
        || CFGetTypeID(stored_account) != CFStringGetTypeID()) return 0;
    CFStringRef expected = CFStringCreateWithCString(NULL, account, kCFStringEncodingUTF8);
    int matches = CFStringCompare(service, CFSTR("io.github.arcadiumclient.launcher.credentials"), 0) == 0
        && expected && CFStringCompare(stored_account, expected, 0) == 0;
    if (expected) CFRelease(expected);
    return matches;
}

static void record(CFDictionaryRef query, const char *operation, OSStatus status) {
    if (fixture_query(query)) {
        fprintf(stderr, "ARCADIUM_KEYCHAIN_STATUS %s %d\n", operation, (int)status);
        fflush(stderr);
    }
}

static OSStatus logged_copy(CFDictionaryRef query, CFTypeRef *result) {
    OSStatus status = SecItemCopyMatching(query, result);
    record(query, "copy", status);
    return status;
}

static OSStatus logged_add(CFDictionaryRef query, CFTypeRef *result) {
    OSStatus status = SecItemAdd(query, result);
    record(query, "add", status);
    return status;
}

static OSStatus logged_update(CFDictionaryRef query, CFDictionaryRef attributes) {
    OSStatus status = SecItemUpdate(query, attributes);
    record(query, "update", status);
    return status;
}

__attribute__((used)) static const struct {
    const void *replacement;
    const void *original;
} interposed[] __attribute__((section("__DATA,__interpose"))) = {
    {(const void *)logged_copy, (const void *)SecItemCopyMatching},
    {(const void *)logged_add, (const void *)SecItemAdd},
    {(const void *)logged_update, (const void *)SecItemUpdate},
};
