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
!ifndef ARCADIUM_NUMERIC_VERSION
!define ARCADIUM_NUMERIC_VERSION "${ARCADIUM_VERSION}.0"
!endif
VIProductVersion "${ARCADIUM_NUMERIC_VERSION}"
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
Var UpdatePid
Var ParentExited
Var TransactionDir
Var StageDir
Var BackupDir
Var TransactionPrepared
Var BackupStarted
Var BackupComplete
Var CommitStarted
Var CommitComplete
Var RollbackFailed
Var FailureHandled
Var TransactionError
Var OldLauncherPresent
Var UninstallBytes
Var FileLength
Var FileLengthOk
Var InstallVolume
Var TempVolume
Var InstallVolumeId
Var TempVolumeId
Var InstallFree
Var TempFree
Var RequiredInstall
Var RequiredTemp
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
    StrCpy $UpdateProcess 0
    StrCpy $ParentExited 0
    StrCpy $TransactionDir ""
    StrCpy $TransactionPrepared 0
    StrCpy $BackupStarted 0
    StrCpy $BackupComplete 0
    StrCpy $CommitStarted 0
    StrCpy $CommitComplete 0
    StrCpy $RollbackFailed 0
    StrCpy $FailureHandled 0
    StrCpy $TransactionError ""
    StrCpy $OldLauncherPresent 0
    SectionSetSize 0 ${ARCADIUM_PAYLOAD_KIB}
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
        StrCpy $UpdatePid $R1
        System::Call 'kernel32::OpenProcess(i 0x00100000, i 0, i $UpdatePid) p .r2'
        StrCpy $UpdateProcess $2
        ${If} $UpdateProcess == 0
            StrCpy $TransactionError "The running launcher could not be identified. Please close it and run this installer again."
            Call FailTransaction
            Abort
        ${EndIf}
        Call PrepareTransaction
        ${If} $TransactionPrepared != 1
            Call FailTransaction
            Abort
        ${EndIf}
        ReadEnvStr $R8 "ARCADIUM_UPDATE_READY"
        ${If} $R8 != ""
            ClearErrors
            FileOpen $R9 "$R8.tmp" w
            ${If} ${Errors}
                StrCpy $TransactionError "The update handoff could not be acknowledged."
                Call FailTransaction
                Abort
            ${EndIf}
            FileWrite $R9 "ready"
            FileClose $R9
            ${IfNot} ${Errors}
                Rename "$R8.tmp" "$R8"
            ${EndIf}
            ${If} ${Errors}
                StrCpy $TransactionError "The update handoff could not be acknowledged."
                Call FailTransaction
                Abort
            ${EndIf}
        ${EndIf}
        System::Call 'kernel32::WaitForSingleObject(p $UpdateProcess, i 30000) i .r3'
        System::Call 'kernel32::CloseHandle(p $UpdateProcess)'
        StrCpy $UpdateProcess 0
        ${If} $3 != 0
            StrCpy $TransactionError "The launcher did not close. Your installed files have not been changed."
            Call FailTransaction
            Abort
        ${EndIf}
        StrCpy $ParentExited 1
    ${EndIf}
FunctionEnd

Function .onInstSuccess
    ${If} $RestartAfterInstall == 1
    ${AndIf} $CommitComplete == 1
        Exec '$\"$INSTDIR\Arcadium.exe$\"'
    ${EndIf}
FunctionEnd

Function ReadFileLength
    Pop $R4
    StrCpy $FileLengthOk 0
    System::Call 'kernel32::CreateFileW(w "$R4", i 0x80, i 7, p 0, i 3, i 0x80, p 0) p .r2'
    ${If} $2 == -1
        Return
    ${EndIf}
    System::Call 'kernel32::GetFileSizeEx(p r2, *l .r3) i .r4'
    System::Call 'kernel32::CloseHandle(p r2)'
    ${If} $4 != 0
        StrCpy $FileLength $3
        StrCpy $FileLengthOk 1
    ${EndIf}
FunctionEnd

!macro VerifyPayloadFile root relativePath expectedBytes
    Push "${root}\${relativePath}"
    Call ReadFileLength
    ${If} $FileLengthOk != 1
    ${OrIf} $FileLength != "${expectedBytes}"
        StrCpy $TransactionError "A required file is missing or incomplete: ${relativePath}"
        Return
    ${EndIf}
!macroend

!macro VerifyStagedFile relativePath expectedBytes
    !insertmacro VerifyPayloadFile "$StageDir" "${relativePath}" "${expectedBytes}"
!macroend

!macro VerifyInstalledFile relativePath expectedBytes
    !insertmacro VerifyPayloadFile "$INSTDIR" "${relativePath}" "${expectedBytes}"
!macroend

Function CheckDiskSpace
    System::Call 'kernel32::GetVolumePathNameW(w "$INSTDIR", w .r1, i ${NSIS_MAX_STRLEN}) i .r0'
    StrCpy $InstallVolume $1
    ${If} $0 == 0
        StrCpy $TransactionError "The installation volume could not be checked."
        Return
    ${EndIf}
    System::Call 'kernel32::GetVolumePathNameW(w "$TEMP", w .r1, i ${NSIS_MAX_STRLEN}) i .r0'
    StrCpy $TempVolume $1
    ${If} $0 == 0
        StrCpy $TransactionError "The temporary volume could not be checked."
        Return
    ${EndIf}
    StrCpy $InstallVolumeId $InstallVolume
    StrCpy $TempVolumeId $TempVolume
    System::Call 'kernel32::GetVolumeNameForVolumeMountPointW(w "$InstallVolume", w .r1, i ${NSIS_MAX_STRLEN}) i .r0'
    ${If} $0 != 0
        StrCpy $InstallVolumeId $1
    ${EndIf}
    System::Call 'kernel32::GetVolumeNameForVolumeMountPointW(w "$TempVolume", w .r1, i ${NSIS_MAX_STRLEN}) i .r0'
    ${If} $0 != 0
        StrCpy $TempVolumeId $1
    ${EndIf}
    System::Call 'kernel32::GetDiskFreeSpaceExW(w "$InstallVolume", *l .r1, p 0, p 0) i .r0'
    StrCpy $InstallFree $1
    ${If} $0 == 0
        StrCpy $TransactionError "Free installation space could not be checked."
        Return
    ${EndIf}
    System::Call 'kernel32::GetDiskFreeSpaceExW(w "$TempVolume", *l .r1, p 0, p 0) i .r0'
    StrCpy $TempFree $1
    ${If} $0 == 0
        StrCpy $TransactionError "Free temporary space could not be checked."
        Return
    ${EndIf}
    System::Int64Op "${ARCADIUM_PAYLOAD_BYTES}" + 67108864
    Pop $RequiredInstall
    StrCpy $RequiredTemp $RequiredInstall
    ${If} $InstallVolumeId == $TempVolumeId
        System::Int64Op $RequiredInstall + "${ARCADIUM_PAYLOAD_BYTES}"
        Pop $RequiredInstall
        StrCpy $RequiredTemp $RequiredInstall
    ${EndIf}
    System::Int64Op $InstallFree < $RequiredInstall
    Pop $R0
    ${If} $R0 != 0
        StrCpy $TransactionError "There is not enough free installation space to update safely. Free some space and try again."
        Return
    ${EndIf}
    System::Int64Op $TempFree < $RequiredTemp
    Pop $R0
    ${If} $R0 != 0
        StrCpy $TransactionError "There is not enough free temporary space to update safely. Free some space and try again."
    ${EndIf}
FunctionEnd

Function ExtractPayload
    ClearErrors
    SetOutPath "$StageDir"
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
    File "${STAGEDIR}\distribution-lock*.json"
    ${If} ${Errors}
        StrCpy $TransactionError "The launcher payload could not be extracted."
        Return
    ${EndIf}
    SetOutPath "$StageDir\components"
    File "${STAGEDIR}\components\*.jar"
    ${If} ${Errors}
        StrCpy $TransactionError "The client components could not be extracted."
        Return
    ${EndIf}
    SetOutPath "$StageDir\licenses"
    File /r "${STAGEDIR}\licenses\*.*"
    ${If} ${Errors}
        StrCpy $TransactionError "The license notices could not be extracted."
        Return
    ${EndIf}
    WriteUninstaller "$StageDir\Uninstall.exe"
    ${If} ${Errors}
        StrCpy $TransactionError "The uninstaller could not be prepared."
        Return
    ${EndIf}
    Push "$StageDir\Uninstall.exe"
    Call ReadFileLength
    ${If} $FileLengthOk != 1
    ${OrIf} $FileLength == 0
        StrCpy $TransactionError "The prepared uninstaller is incomplete."
        Return
    ${EndIf}
    StrCpy $UninstallBytes $FileLength
    !insertmacro ArcadiumManagedPayloadFiles VerifyStagedFile
FunctionEnd

Function CheckUpdatePath
    Pop $R0
    StrLen $R1 $R0
    ${GetParent} "$R0" $R2
    StrLen $R2 $R2
    ${If} $R1 > 259
    ${OrIf} $R2 > 247
        StrCpy $TransactionError "The installation path is too long to update safely. Choose a shorter folder, such as C:\ArcadiumClient."
    ${EndIf}
FunctionEnd

!macro CheckManagedPath relativePath
    Push "$StageDir\${relativePath}"
    Call CheckUpdatePath
    Push "$BackupDir\${relativePath}"
    Call CheckUpdatePath
    Push "$INSTDIR\${relativePath}"
    Call CheckUpdatePath
    ${If} $TransactionError != ""
        Return
    ${EndIf}
!macroend

Function PrepareTransaction
    System::Call 'kernel32::GetCurrentProcessId() i .r0'
    System::Call 'kernel32::GetTickCount64() l .r1'
    StrCpy $TransactionDir "$INSTDIR\.arcadium-update-$0-$1"
    StrCpy $StageDir "$TransactionDir\p"
    StrCpy $BackupDir "$TransactionDir\b"
    ${If} ${FileExists} "$TransactionDir"
        StrCpy $TransactionDir ""
        StrCpy $TransactionError "The update staging directory is already in use."
        Return
    ${EndIf}
    !insertmacro ArcadiumManagedUpdateFiles CheckManagedPath
    Call CheckDiskSpace
    ${If} $TransactionError != ""
        Return
    ${EndIf}
    ClearErrors
    CreateDirectory "$StageDir"
    CreateDirectory "$BackupDir"
    ${If} ${Errors}
        StrCpy $TransactionError "The update staging directory could not be created."
        Return
    ${EndIf}
    Call ExtractPayload
    ${If} $TransactionError != ""
        Return
    ${EndIf}
    StrCpy $TransactionPrepared 1
FunctionEnd

!macro BackupManagedFile relativePath
    ${If} ${FileExists} "$INSTDIR\${relativePath}"
        Push "$INSTDIR\${relativePath}"
        Call ReadFileLength
        ${If} $FileLengthOk != 1
            StrCpy $TransactionError "An installed file cannot be safely replaced: ${relativePath}"
            Return
        ${EndIf}
        ${GetParent} "$BackupDir\${relativePath}" $R4
        ClearErrors
        CreateDirectory "$R4"
        Rename "$INSTDIR\${relativePath}" "$BackupDir\${relativePath}"
        ${If} ${Errors}
            StrCpy $TransactionError "An installed file is in use or could not be backed up: ${relativePath}"
            Return
        ${EndIf}
    ${EndIf}
!macroend

!macro BackupOtherManagedFile relativePath
    ${If} "${relativePath}" != "Arcadium.exe"
        !insertmacro BackupManagedFile "${relativePath}"
    ${EndIf}
!macroend

Function BackupUpdate
    ${If} ${FileExists} "$INSTDIR\Arcadium.exe"
        StrCpy $OldLauncherPresent 1
    ${EndIf}
    StrCpy $BackupStarted 1
    !insertmacro ArcadiumManagedUpdateFiles BackupOtherManagedFile
    StrCpy $BackupComplete 1
FunctionEnd

!macro MoveStagedFile relativePath
    ${GetParent} "$INSTDIR\${relativePath}" $R4
    ClearErrors
    CreateDirectory "$R4"
    Rename "$StageDir\${relativePath}" "$INSTDIR\${relativePath}"
    ${If} ${Errors}
        StrCpy $TransactionError "A prepared file could not be installed: ${relativePath}"
        Return
    ${EndIf}
!macroend

!macro CommitPayloadFile relativePath expectedBytes
    ${If} "${relativePath}" != "Arcadium.exe"
        !insertmacro MoveStagedFile "${relativePath}"
    ${EndIf}
!macroend

Function CommitUpdate
    StrCpy $CommitStarted 1
    !insertmacro ArcadiumManagedPayloadFiles CommitPayloadFile
    !insertmacro MoveStagedFile "Uninstall.exe"
    !insertmacro BackupManagedFile "Arcadium.exe"
    !insertmacro MoveStagedFile "Arcadium.exe"
    !insertmacro ArcadiumManagedPayloadFiles VerifyInstalledFile
    !insertmacro VerifyPayloadFile "$INSTDIR" "Uninstall.exe" "$UninstallBytes"
    StrCpy $CommitComplete 1
FunctionEnd

!macro RestoreOneFile relativePath
    ${If} ${FileExists} "$BackupDir\${relativePath}"
        ${If} ${FileExists} "$INSTDIR\${relativePath}"
            ClearErrors
            Delete "$INSTDIR\${relativePath}"
            ${If} ${Errors}
                StrCpy $RollbackFailed 1
            ${EndIf}
        ${EndIf}
        ${GetParent} "$INSTDIR\${relativePath}" $R4
        ClearErrors
        CreateDirectory "$R4"
        Rename "$BackupDir\${relativePath}" "$INSTDIR\${relativePath}"
        ${If} ${Errors}
            StrCpy $RollbackFailed 1
        ${EndIf}
    ${ElseIf} $CommitStarted == 1
        ${IfNot} ${FileExists} "$StageDir\${relativePath}"
            ${If} ${FileExists} "$INSTDIR\${relativePath}"
                ClearErrors
                Delete "$INSTDIR\${relativePath}"
                ${If} ${Errors}
                    StrCpy $RollbackFailed 1
                ${EndIf}
            ${EndIf}
        ${EndIf}
    ${EndIf}
!macroend

!macro RestoreManagedFile relativePath
    ${If} "${relativePath}" != "Arcadium.exe"
        !insertmacro RestoreOneFile "${relativePath}"
    ${EndIf}
!macroend

Function RollbackUpdate
    !insertmacro ArcadiumManagedUpdateFiles RestoreManagedFile
    ${If} $RollbackFailed == 0
        !insertmacro RestoreOneFile "Arcadium.exe"
    ${EndIf}
    ${If} $OldLauncherPresent == 1
    ${AndIfNot} ${FileExists} "$INSTDIR\Arcadium.exe"
        StrCpy $RollbackFailed 1
    ${EndIf}
FunctionEnd

!macro DeleteTransactionFile relativePath
    Delete "$StageDir\${relativePath}"
    Delete "$BackupDir\${relativePath}"
!macroend

!macro RemoveTransactionDirectory relativePath
    RMDir "$StageDir\${relativePath}"
    RMDir "$BackupDir\${relativePath}"
!macroend

Function CleanupTransaction
    ${If} $TransactionDir == ""
        Return
    ${EndIf}
    SetOutPath "$TEMP"
    !insertmacro ArcadiumManagedUpdateFiles DeleteTransactionFile
    !insertmacro ArcadiumManagedUpdateDirectories RemoveTransactionDirectory
    RMDir "$StageDir"
    RMDir "$BackupDir"
    Delete "$TransactionDir\failure.txt"
    RMDir "$TransactionDir"
FunctionEnd

Function FailTransaction
    ${If} $FailureHandled == 1
        Return
    ${EndIf}
    StrCpy $FailureHandled 1
    ${If} $UpdateProcess != 0
        System::Call 'kernel32::CloseHandle(p $UpdateProcess)'
        StrCpy $UpdateProcess 0
    ${EndIf}
    ${If} $BackupStarted == 1
        Call RollbackUpdate
    ${EndIf}
    ${If} $RollbackFailed == 0
        Call CleanupTransaction
    ${Else}
        FileOpen $R9 "$TransactionDir\failure.txt" w
        FileWrite $R9 "$TransactionError$\r$\nPrevious files are preserved in: $BackupDir$\r$\n"
        FileClose $R9
    ${EndIf}
    ReadEnvStr $R8 "ARCADIUM_UPDATE_READY"
    ${If} $R8 != ""
        FileOpen $R9 "$R8.error" w
        FileWrite $R9 "$TransactionError$\r$\nRollback failed: $RollbackFailed$\r$\nRecovery: $TransactionDir$\r$\n"
        FileClose $R9
    ${EndIf}
    ${If} $AutoUpdate == 1
    ${AndIf} $ParentExited == 1
    ${AndIf} $RollbackFailed == 0
    ${AndIf} $OldLauncherPresent == 1
        System::Call 'kernel32::SetEnvironmentVariableW(w "ARCADIUM_UPDATE_FAILED", w "${ARCADIUM_VERSION}")'
        ${If} $RestartAfterInstall == 1
            Exec '$\"$INSTDIR\Arcadium.exe$\"'
        ${EndIf}
    ${EndIf}
    ${If} $RollbackFailed == 1
        MessageBox MB_OK|MB_ICONSTOP "The update could not be completed. Previous files are preserved in:$\r$\n$BackupDir$\r$\n$TransactionError" /SD IDOK
    ${Else}
        MessageBox MB_OK|MB_ICONSTOP "$TransactionError$\r$\nYour previous installed files have been kept." /SD IDOK
    ${EndIf}
    SetErrorLevel 5
FunctionEnd

Function .onInstFailed
    Call FailTransaction
FunctionEnd

Section "Arcadium Client"
    SetShellVarContext current
    ${If} $TransactionPrepared != 1
        Call PrepareTransaction
    ${EndIf}
    ${If} $TransactionPrepared != 1
        Call FailTransaction
        Abort
    ${EndIf}
    Call BackupUpdate
    ${If} $BackupComplete != 1
        Call FailTransaction
        Abort
    ${EndIf}
    Call CommitUpdate
    ${If} $CommitComplete != 1
        Call FailTransaction
        Abort
    ${EndIf}
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
    Call CleanupTransaction
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
