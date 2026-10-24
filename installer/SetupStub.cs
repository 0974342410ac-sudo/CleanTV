// Bo cai "Chinh sua Launcher TV": giai nen install.ps1 + payload.zip (nhung trong exe) ra thu muc tam,
// chay install.ps1 bang PowerShell co san cua Windows, roi don thu muc tam.
// Build bang installer\build-setup.ps1 (csc.exe cua .NET Framework 4).
using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows.Forms;

static class Setup
{
    [STAThread]
    static int Main(string[] args)
    {
        string dir = Path.Combine(Path.GetTempPath(), "ChinhSuaLauncher-setup-" + Guid.NewGuid().ToString("N"));
        try
        {
            Directory.CreateDirectory(dir);
            Extract("install.ps1", Path.Combine(dir, "install.ps1"));
            Extract("payload.zip", Path.Combine(dir, "payload.zip"));

            string ps = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),
                @"WindowsPowerShell\v1.0\powershell.exe");
            string extra = Array.IndexOf(args, "/quiet") >= 0 || Array.IndexOf(args, "-Quiet") >= 0 ? " -Quiet" : "";
            var psi = new ProcessStartInfo(ps,
                "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \"" + Path.Combine(dir, "install.ps1") + "\"" + extra);
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.WorkingDirectory = dir;
            using (var p = Process.Start(psi))
            {
                p.WaitForExit();
                return p.ExitCode;
            }
        }
        catch (Exception e)
        {
            MessageBox.Show("Cài đặt thất bại:\n" + e.Message, "Chỉnh sửa Launcher TV",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
        finally
        {
            try { Directory.Delete(dir, true); } catch { }
        }
    }

    static void Extract(string name, string path)
    {
        using (Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream(name))
        using (FileStream f = File.Create(path))
        {
            if (s == null) throw new FileNotFoundException("Thiếu dữ liệu cài đặt: " + name);
            s.CopyTo(f);
        }
    }
}
