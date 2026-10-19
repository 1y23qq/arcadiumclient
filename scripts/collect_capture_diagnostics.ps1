param(
    [string]$OutputDirectory = (Join-Path (Get-Location) 'arcadium-capture-diagnostics'),
    [int]$GameProcessId = 0
)

$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') {
    throw 'Capture diagnostics require Windows.'
}

if (-not ('ArcadiumCaptureDiagnostics.Native' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

namespace ArcadiumCaptureDiagnostics {
    public sealed class WindowInfo {
        public int ProcessId;
        public string Executable;
        public string ExecutablePath;
        public string Title;
        public string WindowClass;
        public string WindowHandle;
        public bool Visible;
        public bool Minimized;
        public bool Foreground;
        public int Width;
        public int Height;
        public uint DisplayAffinity;
        public bool DisplayAffinityAvailable;
    }

    public static class Native {
        private delegate bool EnumWindowProc(IntPtr window, IntPtr parameter);
        [StructLayout(LayoutKind.Sequential)]
        private struct Rect { public int Left, Top, Right, Bottom; }
        [DllImport("user32.dll")]
        private static extern bool EnumWindows(EnumWindowProc callback, IntPtr parameter);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern int GetWindowText(IntPtr window, StringBuilder text, int count);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern int GetClassName(IntPtr window, StringBuilder text, int count);
        [DllImport("user32.dll")]
        private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);
        [DllImport("user32.dll")]
        private static extern bool IsWindowVisible(IntPtr window);
        [DllImport("user32.dll")]
        private static extern bool IsIconic(IntPtr window);
        [DllImport("user32.dll")]
        private static extern IntPtr GetForegroundWindow();
        [DllImport("user32.dll")]
        private static extern bool GetWindowRect(IntPtr window, out Rect rect);
        [DllImport("user32.dll")]
        private static extern bool GetWindowDisplayAffinity(IntPtr window, out uint affinity);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr OpenProcess(uint access, bool inheritHandle, uint processId);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool QueryFullProcessImageName(IntPtr process, uint flags, StringBuilder path, ref uint size);
        [DllImport("kernel32.dll")]
        private static extern bool CloseHandle(IntPtr handle);

        public static WindowInfo[] Collect() {
            var result = new List<WindowInfo>();
            IntPtr foreground = GetForegroundWindow();
            EnumWindowProc callback = delegate(IntPtr window, IntPtr parameter) {
                var title = new StringBuilder(1024);
                GetWindowText(window, title, title.Capacity);
                if (!title.ToString().StartsWith("Arcadium Client 1.8.9", StringComparison.Ordinal)) return true;
                uint processId;
                GetWindowThreadProcessId(window, out processId);
                string executable;
                try {
                    using (Process process = Process.GetProcessById((int)processId)) executable = process.ProcessName + ".exe";
                } catch (ArgumentException) { return true; }
                  catch (InvalidOperationException) { return true; }
                if (!executable.Equals("java.exe", StringComparison.OrdinalIgnoreCase)
                    && !executable.Equals("javaw.exe", StringComparison.OrdinalIgnoreCase)) return true;
                var className = new StringBuilder(256);
                GetClassName(window, className, className.Capacity);
                string executablePath = null;
                IntPtr processHandle = OpenProcess(0x1000, false, processId);
                if (processHandle != IntPtr.Zero) {
                    try {
                        var path = new StringBuilder(32768);
                        uint size = (uint)path.Capacity;
                        if (QueryFullProcessImageName(processHandle, 0, path, ref size)) executablePath = path.ToString();
                    } finally { CloseHandle(processHandle); }
                }
                Rect rect;
                GetWindowRect(window, out rect);
                uint affinity;
                bool affinityAvailable = GetWindowDisplayAffinity(window, out affinity);
                result.Add(new WindowInfo {
                    ProcessId = (int)processId,
                    Executable = executable,
                    ExecutablePath = executablePath,
                    Title = title.ToString(),
                    WindowClass = className.ToString(),
                    WindowHandle = "0x" + window.ToInt64().ToString("X"),
                    Visible = IsWindowVisible(window),
                    Minimized = IsIconic(window),
                    Foreground = window == foreground,
                    Width = Math.Max(0, rect.Right - rect.Left),
                    Height = Math.Max(0, rect.Bottom - rect.Top),
                    DisplayAffinity = affinity,
                    DisplayAffinityAvailable = affinityAvailable
                });
                return true;
            };
            EnumWindows(callback, IntPtr.Zero);
            GC.KeepAlive(callback);
            return result.ToArray();
        }
    }
}
'@
}

$windows = @([ArcadiumCaptureDiagnostics.Native]::Collect())
if ($GameProcessId -gt 0) {
    $windows = @($windows | Where-Object { $_.ProcessId -eq $GameProcessId })
}
$profilePrefix = $env:USERPROFILE
foreach ($window in $windows) {
    if ($window.ExecutablePath -and $profilePrefix -and $window.ExecutablePath.StartsWith($profilePrefix, [StringComparison]::OrdinalIgnoreCase)) {
        $window.ExecutablePath = '%USERPROFILE%' + $window.ExecutablePath.Substring($profilePrefix.Length)
    }
}

$report = [ordered]@{
    schemaVersion = 1
    collectedAtUtc = [DateTime]::UtcNow.ToString('o')
    product = 'Arcadium Client 1.8.9'
    windows = $windows
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$outputPath = (Resolve-Path -LiteralPath $OutputDirectory).Path
$jsonPath = Join-Path $outputPath 'arcadium-capture.json'
$textPath = Join-Path $outputPath 'arcadium-capture.txt'
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $jsonPath -Encoding UTF8
$text = @('Arcadium Client 1.8.9 capture diagnostics', '')
foreach ($window in $windows) {
    $text += @(
        "Window Title: $($window.Title)",
        "Class Name: $($window.WindowClass)",
        "Process ID: $($window.ProcessId)",
        "Process Filename: $($window.Executable)",
        "Executable Path: $($window.ExecutablePath)",
        "Window Handle: $($window.WindowHandle)",
        "Visible: $($window.Visible)",
        "Minimized: $($window.Minimized)",
        "Foreground: $($window.Foreground)",
        "Window Size: $($window.Width) x $($window.Height)",
        "Display Affinity: $($window.DisplayAffinity) (available: $($window.DisplayAffinityAvailable))",
        ''
    )
}
if ($windows.Count -eq 0) {
    $text += 'No Arcadium Client 1.8.9 game window found. Start the game and run this script again.'
}
$text | Set-Content -LiteralPath $textPath -Encoding UTF8
Write-Output "Capture diagnostics: $jsonPath"
Write-Output "Support summary: $textPath"
if ($windows.Count -eq 0) { Write-Warning 'No Arcadium game window found.' }
