# Gradle
$Build = $true

# JVM
$Xms = "1G"
$Xmx = "2G"
$Debug = $true
$Jar = "paper.jar"

# Spigot
$Version = "1.16.5"

# Plugins
$Plugins = (
    "https://github.com/monun/kotlin-plugin/releases/download/1.4.21/Kotlin.jar",
    "https://github.com/monun/invfx/releases/download/1.4.0/InvFX.jar",
    "https://ci.dmulloy2.net/job/ProtocolLib/lastSuccessfulBuild/artifact/target/ProtocolLib.jar"
)

# Backup
$AskBackup = $false

Function Download-File
{
    [CmdletBinding()]
    Param (
        [string]$Url,
        [string]$Folder,
        [Parameter(Mandatory = $False)] [string]$Filename
    )

    Create-Directory $Folder

    $Location = (Get-Location).Path
    $Destfolder = Resolve-Path("$Location/$Folder")

    try
    {
        $WebRequest = [System.Net.HttpWebRequest]::Create($Url);
        $WebRequest.Method = "GET"
        $WebResponse = $WebRequest.GetResponse()

        if ( [string]::IsNullOrEmpty($Filename))
        {
            $Disposition = $WebResponse.Headers['Content-Disposition']

            if ( [string]::IsNullOrEmpty($Disposition))
            {
                $Filename = [System.IO.Path]::GetFileName($Url)
            }
            else
            {
                $Filename = [System.Net.Mime.ContentDisposition]::new($Disposition).FileName
            }
        }

        $Dest = "$Destfolder/$Filename"
        $FileInfo = [System.IO.FileInfo]$Dest

        if (Test-Path $Dest)
        {
            $RemoteLastModified = $WebResponse.LastModified
            $LocalLastModified = $FileInfo.LastWriteTime

            if ([datetime]::Compare($RemoteLastModified, $LocalLastModified) -eq 0)
            {
                Write-Host "UP-TO-DATE $Filename($Url)"
                $WebResponse.Dispose()
                return
            }
            Write-Host "Updating $Filename from $url"
        }
        else
        {
            Write-Host "Downloading $Filename from $url"
        }
        $ResponseStream = $WebResponse.GetResponseStream()
        $FileWriter = New-Object System.IO.FileStream ($Dest, [System.IO.FileMode]::Create)
        [byte[]]$buffer = New-Object byte[] 4096

        do
        {
            $length = $ResponseStream.Read($buffer, 0, 4096)
            $FileWriter.Write($buffer, 0, $length)
        } while ($length -ne 0)

        $ResponseStream.Close()
        $FileWriter.Close()
        $FileInfo.LastWriteTime = $WebResponse.LastModified
    }
    catch [System.Net.WebException]
    {
        $Status = $_.Exception.Response.StatusCode
        $Msg = $_.Exception
        Write-Host "  Failed to dowloading $Dest, Status code: $Status - $Msg" -ForegroundColor Red
    }

}

Function Create-Directory([string]$PathName)
{
    if (!(Test-Path $PathName))
    {
        New-Item $PathName -Type Directory | Out-Null
    }
}

Function Agree-EULA
{
    $Eula = "eula.txt"
    if (!(Test-Path $Eula))
    {
        New-Item $Eula -ItemType "file" | Out-Null
        Add-Content $Eula "eula=true"
    }
}

function Choice
{
    Param(
        [string]$Prompt,
        [string]$Choice,
        [char]$Default,
        [int]$Seconds
    )

    $Choice = $Choice.ToUpper()
    $StartTime = Get-Date
    $TimeOut = New-TimeSpan -Seconds $Seconds

    Write-Host $Prompt

    $Choose = $Default

    while ($CurrentTime -lt $StartTime + $TimeOut)
    {
        if ($host.UI.RawUI.KeyAvailable)
        {
            [string]$Key = ($host.UI.RawUI.ReadKey("IncludeKeyDown,NoEcho")).character
            $Key = $Key.ToUpper().ToCharArray()[0]

            if ( $Choice.Contains($Key))
            {
                $Choose = $Key
                Break
            }
        }

        $CurrentTime = Get-Date
    }

    Write-Host $Choose

    return $Choose
}

Function Backup
{
    $Backup = "backup"

    Create-Directory $Backup

    $Date = Get-Date -Format "yyyy-MM-dd HHmmss"
    $ArchiveName = "$Backup/$Date.zip"
    7z a -tzip $ArchiveName ./ "-xr!*.gz" "-x!paper.jar" "-x!paper.ps1" "-x!backup" "-x!cache" | Out-Null
    Write-Host "Backup completed $ArchiveName"
}

$ProjectFolder = Get-Location
$Folder = ".$( (Get-Item($MyInvocation.MyCommand.Name)).BaseName )"

# Setup
Create-Directory $Folder
Set-Location $Folder
$host.UI.RawUI.WindowTitle = "paper $Version"
Agree-EULA

# Download paper
Download-File "https://papermc.io/api/v1/paper/$Version/latest/download" "." $Jar

# Download plugins
foreach ($Plugin in $Plugins)
{
    Download-File $Plugin "plugins"
}

# Build JVM args
$JVMArgs = [System.Collections.ArrayList]@(
"-Xms$Xms",
"-Xmx$Xmx"
)

if ($Debug)
{
    [void]$JVMArgs.Add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
}

[void]$JVMArgs.Add("-jar")
[void]$JVMArgs.Add($Jar)
[void]$JVMArgs.Add("--nogui")

# Run
While ($true)
{
    if ($Build)
    {
        Set-Location $ProjectFolder
        ./gradlew clean paper
        Set-Location $Folder
    }

    java $JVMArgs

    if ($AskBackup)
    {
        $SkipBackup = Choice "Skip the backup? [Y]es, [N]o" @('Y', 'N') 'N' 3

        if ($SkipBackup -eq 'N')
        {
            Backup
        }
    }

    $Restart = Choice "Restart? [Y]es [N]o" @('Y', 'N') 'Y' 2

    if ($Restart -eq 'N')
    {
        Set-Location $ProjectFolder
        Break
    }
}