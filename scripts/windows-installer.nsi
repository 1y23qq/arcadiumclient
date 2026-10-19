Unicode true
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "x64.nsh"
!include "FileFunc.nsh"
!include "${UPDATEFILES}"

Name "Arcadium Client"
OutFile "${OUTPUT}"
InstallDir "$LOCALAPPDATA\Programs\ArcadiumClient"
InstallDirRegKey HKCU "Software\ArcadiumClient" "InstallPath"
RequestExecutionLevel user
SetCompressor /SOLID lzma
SetCompressorDictSize 32
Icon "${ICON}"
UninstallIcon "${ICON}"
VIProductVersion "${ARCADIUM_VERSION}.0"
VIAddVersionKey "ProductName" "Arcadium Client"
VIAddVersionKey "CompanyName" "0aurorq3"
VIAddVersionKey "FileDescription" "Arcadium Client Installer"
VIAddVersionKey "LegalCopyright" "Copyright 2026 0aurorq3"
VIAddVersionKey "FileVersion" "${ARCADIUM_VERSION}"
VIAddVersionKey "ProductVersion" "${ARCADIUM_VERSION}"

Var TestInstall
Var AutoUpdate
Var RestartAfterInstall
Var UpdateProcess
Var UpdateBackupReady
!macro DeleteManagedFile relative
    Delete "$INSTDIR\${relative}"
!macroend
!macro RemoveManagedDirectory relative
    RMDir "$INSTDIR\${relative}"
!macroend
!define MUI_ICON "${ICON}"
!define MUI_UNICON "${ICON}"
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\Arcadium.exe"
!define MUI_FINISHPAGE_RUN_NOTCHECKED
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${STAGEDIR}\LICENSE"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "Japanese"

Function .onInit
    ${IfNot} ${RunningX64}
        MessageBox MB_OK|MB_ICONSTOP "Arcadium Client requires 64-bit Windows."
        Abort
    ${EndIf}
    StrCpy $TestInstall 0
    StrCpy $AutoUpdate 0
    StrCpy $RestartAfterInstall 0
    StrCpy $UpdateBackupReady 0
    ${GetParameters} $R0
    ClearErrors
    ${GetOptions} $R0 "/TESTINSTALL" $R1
    ${IfNot} ${Errors}
        StrCpy $TestInstall 1
    ${EndIf}
    ClearErrors
    ${GetOptions} $R0 "/RESTART" $R1
    ${IfNot} ${Errors}
        StrCpy $RestartAfterInstall 1
    ${EndIf}
    ClearErrors
    ${GetOptions} $R0 "/AUTOUPDATE" $R1
    ${IfNot} ${Errors}
        StrCpy $AutoUpdate 1
        ClearErrors
        ${GetOptions} $R0 "/UPDATEPID=" $R1
        ${If} ${Errors}
            SetErrorLevel 2
            Abort
        ${EndIf}
        System::Call 'kernel32::OpenProcess(i 0x00100000, i 0, i $R1) p .r2'
        StrCpy $UpdateProcess $2
        ReadEnvStr $R8 "ARCADIUM_UPDATE_READY"
        ${If} $R8 != ""
            ClearErrors
            FileOpen $R9 "$R8" w
            ${If} ${Errors}
                SetErrorLevel 6
                Abort
            ${EndIf}
            FileWrite $R9 "ready"
            FileClose $R9
        ${EndIf}
        ${If} $UpdateProcess != 0
            System::Call 'kernel32::WaitForSingleObject(p $UpdateProcess, i 30000) i .r3'
            System::Call 'kernel32::CloseHandle(p $UpdateProcess)'
            ${If} $3 != 0
                SetErrorLevel 3
                Abort
            ${EndIf}
        ${EndIf}
    ${EndIf}
FunctionEnd

Function .onInstSuccess
    ${If} $RestartAfterInstall == 1
        Exec '$\"$INSTDIR\Arcadium.exe$\"'
    ${EndIf}
FunctionEnd

!macro BackupManagedFile relativePath
    ${If} ${FileExists} "$INSTDIR\${relativePath}"
        ${GetParent} "$PLUGINSDIR\rollback\${relativePath}" $R4
        CreateDirectory "$R4"
        System::Call 'kernel32::CopyFileW(w "$INSTDIR\${relativePath}", w "$PLUGINSDIR\rollback\${relativePath}", i 0) i .r3'
        ${If} $3 == 0
            SetErrorLevel 4
            Abort
        ${EndIf}
    ${EndIf}
!macroend

!macro RestoreManagedFile relativePath
    ${If} ${FileExists} "$PLUGINSDIR\rollback\${relativePath}"
        System::Call 'kernel32::CopyFileW(w "$PLUGINSDIR\rollback\${relativePath}", w "$INSTDIR\${relativePath}", i 0) i .r3'
    ${EndIf}
!macroend

Function BackupUpdate
    InitPluginsDir
    CreateDirectory "$PLUGINSDIR\rollback"
    CreateDirectory "$PLUGINSDIR\rollback\components"
    CreateDirectory "$PLUGINSDIR\rollback\licenses"
    !insertmacro ArcadiumManagedUpdateFiles BackupManagedFile
    StrCpy $UpdateBackupReady 1
FunctionEnd

Function .onInstFailed
    ${If} $AutoUpdate == 1
        ${If} $UpdateBackupReady == 1
            !insertmacro ArcadiumManagedUpdateFiles RestoreManagedFile
        ${EndIf}
        System::Call 'kernel32::SetEnvironmentVariableW(w "ARCADIUM_UPDATE_FAILED", w "${ARCADIUM_VERSION}")'
        ${If} $RestartAfterInstall == 1
            Exec '$\"$INSTDIR\Arcadium.exe$\"'
        ${EndIf}
    ${EndIf}
FunctionEnd

Section "Arcadium Client"
    SetShellVarContext current
    ${If} $AutoUpdate == 1
        Call BackupUpdate
    ${EndIf}
    ClearErrors
    SetOutPath "$INSTDIR"
    File "${STAGEDIR}\Arcadium.exe"
    File "${STAGEDIR}\concrt140.dll"
    File "${STAGEDIR}\msvcp140.dll"
    File "${STAGEDIR}\msvcp140_1.dll"
    File "${STAGEDIR}\msvcp140_2.dll"
    File "${STAGEDIR}\msvcp140_atomic_wait.dll"
    File "${STAGEDIR}\msvcp140_codecvt_ids.dll"
    File "${STAGEDIR}\vccorlib140.dll"
    File "${STAGEDIR}\vcruntime140.dll"
    File "${STAGEDIR}\vcruntime140_1.dll"
    File "${STAGEDIR}\vcruntime140_threads.dll"
    File "${STAGEDIR}\LICENSE"
    File "${STAGEDIR}\NOTICE.md"
    File "${STAGEDIR}\README.md"
    File "${STAGEDIR}\distribution-lock.json"
    SetOutPath "$INSTDIR\components"
    File "${STAGEDIR}\components\*.jar"
    SetOutPath "$INSTDIR\licenses"
    File /r "${STAGEDIR}\licenses\*.*"
    ${If} ${Errors}
        SetErrorLevel 5
        Abort
    ${EndIf}
    WriteUninstaller "$INSTDIR\Uninstall.exe"
    ${If} $TestInstall != 1
        WriteRegStr HKCU "Software\ArcadiumClient" "InstallPath" "$INSTDIR"
        WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ArcadiumClient" "DisplayName" "Arcadium Client"
        WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ArcadiumClient" "DisplayVersion" "${ARCADIUM_VERSION}"
        WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ArcadiumClient" "Publisher" "0aurorq3"
        WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ArcadiumClient" "DisplayIcon" "$INSTDIR\Arcadium.exe"
        WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ArcadiumClient" "UninstallString" '$\"$INSTDIR\Uninstall.exe$\"'
        WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ArcadiumClient" "QuietUninstallString" '$\"$INSTDIR\Uninstall.exe$\" /S'
        WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ArcadiumClient" "NoModify" 1
        WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ArcadiumClient" "NoRepair" 1
        WriteRegStr HKCU "Software\Classes\arcadium" "" "URL:Arcadium Protocol"
        WriteRegStr HKCU "Software\Classes\arcadium" "URL Protocol" ""
        WriteRegStr HKCU "Software\Classes\arcadium\DefaultIcon" "" "$INSTDIR\Arcadium.exe,0"
        WriteRegStr HKCU "Software\Classes\arcadium\shell\open\command" "" '$\"$INSTDIR\Arcadium.exe$\" $\"%1$\"'
        ${If} $AutoUpdate != 1
        CreateDirectory "$SMPROGRAMS\Arcadium Client"
        CreateShortcut "$SMPROGRAMS\Arcadium Client\Arcadium Client.lnk" "$INSTDIR\Arcadium.exe"
        CreateShortcut "$SMPROGRAMS\Arcadium Client\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
        CreateShortcut "$DESKTOP\Arcadium Client.lnk" "$INSTDIR\Arcadium.exe"
        ${EndIf}
    ${EndIf}
SectionEnd

Function un.onInit
    StrCpy $TestInstall 0
    ${GetParameters} $R0
    ClearErrors
    ${GetOptions} $R0 "/TESTINSTALL" $R1
    ${IfNot} ${Errors}
        StrCpy $TestInstall 1
    ${EndIf}
FunctionEnd

Section "Uninstall"
    SetShellVarContext current
    !insertmacro ArcadiumManagedUpdateFiles DeleteManagedFile
    Delete "$INSTDIR\Arcadium.exe"
    Delete "$INSTDIR\concrt140.dll"
    Delete "$INSTDIR\msvcp140.dll"
    Delete "$INSTDIR\msvcp140_1.dll"
    Delete "$INSTDIR\msvcp140_2.dll"
    Delete "$INSTDIR\msvcp140_atomic_wait.dll"
    Delete "$INSTDIR\msvcp140_codecvt_ids.dll"
    Delete "$INSTDIR\vccorlib140.dll"
    Delete "$INSTDIR\vcruntime140.dll"
    Delete "$INSTDIR\vcruntime140_1.dll"
    Delete "$INSTDIR\vcruntime140_threads.dll"
    Delete "$INSTDIR\LICENSE"
    Delete "$INSTDIR\NOTICE.md"
    Delete "$INSTDIR\README.md"
    Delete "$INSTDIR\distribution-lock.json"
    Delete "$INSTDIR\components\ArcadiumCore-1.8.9.jar"
    Delete "$INSTDIR\components\ArcadiumFeatures-1.8.9.jar"
    Delete "$INSTDIR\components\ArcadiumRenderer-1.8.9.jar"
    Delete "$INSTDIR\components\ArcadiumForgeCompat-1.8.9.jar"
    RMDir "$INSTDIR\components"
    Delete "$INSTDIR\licenses\Inter-OFL.txt"
    Delete "$INSTDIR\licenses\JetBrainsMono-OFL.txt"
    Delete "$INSTDIR\licenses\Poppins-OFL.txt"
    Delete "$INSTDIR\licenses\NSIS-COPYING.txt"
    Delete "$INSTDIR\licenses\UPSTREAM-ATTRIBUTION.md"
    Delete "$INSTDIR\licenses\UPSTREAM-GPL-3.0.txt"
    Delete "$INSTDIR\licenses\UPSTREAM-CLIENT-UI.txt"
    Delete "$INSTDIR\licenses\UPSTREAM-CLIENT-FEATURES.md"
    Delete "$INSTDIR\licenses\LEGACY-ASSETS.md"
    Delete "$INSTDIR\licenses\Rust-dependencies.txt"
    RMDir "$INSTDIR\licenses"
    Delete "$INSTDIR\Uninstall.exe"
    !insertmacro ArcadiumManagedUpdateDirectories RemoveManagedDirectory
    RMDir "$INSTDIR"
    ${If} $TestInstall != 1
        Delete "$DESKTOP\Arcadium Client.lnk"
        Delete "$SMPROGRAMS\Arcadium Client\Arcadium Client.lnk"
        Delete "$SMPROGRAMS\Arcadium Client\Uninstall.lnk"
        RMDir "$SMPROGRAMS\Arcadium Client"
        DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ArcadiumClient"
        DeleteRegKey HKCU "Software\ArcadiumClient"
        ReadRegStr $R0 HKCU "Software\Classes\arcadium\shell\open\command" ""
        ${If} $R0 == '$\"$INSTDIR\Arcadium.exe$\" $\"%1$\"'
            DeleteRegKey HKCU "Software\Classes\arcadium"
        ${EndIf}
    ${EndIf}
SectionEnd
