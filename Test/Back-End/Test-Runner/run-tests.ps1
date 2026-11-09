param([string]$Only)

$e = [char]27
$B = "$e[1m"; $D = "$e[2m"; $R = "$e[0m"; $INV = "$e[7m"
$RED = "$e[31m"; $GRN = "$e[32m"; $YEL = "$e[33m"; $CYN = "$e[36m"
$POINTER = [char]0x276F
$PLAY = [char]0x25B6
$LINE = ([string][char]0x2500) * 50

$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\..\.."))
$interactive = (-not $Only) -and (-not [Console]::IsInputRedirected) -and (-not [Console]::IsOutputRedirected)

if (-not $env:TEST_LOGIN_NAME) { $env:TEST_LOGIN_NAME = "joetest" }
if (-not $env:TEST_LOGIN_PASSWORD) { $env:TEST_LOGIN_PASSWORD = "123" }

function Write-Header {
    Write-Host ""
    Write-Host "  $CYN$B$('=' * 50)$R"
    Write-Host "  $CYN$B    3940 Group Assignment  -  Back-end tests$R"
    Write-Host "  $CYN$B$('=' * 50)$R"
    Write-Host ""
}

function Get-TestClasses {
    Get-ChildItem (Join-Path $root "Test\Back-End\*.java") | Sort-Object Name | ForEach-Object {
        [pscustomobject]@{
            Name     = $_.BaseName
            Selected = $true
            Tomcat   = [bool](Select-String -Path $_.FullName -Pattern "ensureTomcatIsUp" -Quiet)
        }
    }
}

function Show-Picker($tests) {
    $count = @($tests).Count
    $runIndex = $count + 1
    $pos = $runIndex
    $message = ""

    Clear-Host
    Write-Header
    Write-Host "  ${B}Select tests to run$R"
    Write-Host "  $D$([char]0x2191)/$([char]0x2193) move   Enter tick/untick   Q quit$R"
    Write-Host ""
    $top = [Console]::CursorTop
    [Console]::CursorVisible = $false

    try {
        while ($true) {
            [Console]::SetCursorPosition(0, $top)
            $picked = @($tests | Where-Object { $_.Selected }).Count

            for ($i = 0; $i -le $runIndex; $i++) {
                $here = ($i -eq $pos)
                $arrow = if ($here) { "$CYN$B$POINTER$R" } else { " " }

                if ($i -eq 0) {
                    if ($picked -eq $count) { $box = "$GRN[x]$R" }
                    elseif ($picked -eq 0) { $box = "$D[ ]$R" }
                    else { $box = "$YEL[-]$R" }
                    $label = if ($here) { "$CYN${B}All tests$R" } else { "${B}All tests$R" }
                    Write-Host "  $arrow $box $label$e[K"
                    Write-Host "      $D$LINE$R$e[K"
                }
                elseif ($i -eq $runIndex) {
                    Write-Host "      $D$LINE$R$e[K"
                    $text = "$PLAY  Run selected tests ($picked)"
                    $label = if ($here) { "$INV$GRN$B $text $R" } else { "$GRN$B $text $R" }
                    Write-Host "  $arrow $label$e[K"
                }
                else {
                    $t = $tests[$i - 1]
                    $box = if ($t.Selected) { "$GRN[x]$R" } else { "$D[ ]$R" }
                    $name = if ($here) { "$CYN$B$($t.Name)$R" } elseif ($t.Selected) { $t.Name } else { "$D$($t.Name)$R" }
                    $tag = if ($t.Tomcat) { "  ${D}uses Tomcat$R" } else { "" }
                    Write-Host "  $arrow $box $name$tag$e[K"
                }
            }
            Write-Host ""
            Write-Host "  $message$e[K"

            $key = [Console]::ReadKey($true)
            $message = ""
            switch ($key.Key) {
                "UpArrow"   { $pos = if ($pos -eq 0) { $runIndex } else { $pos - 1 } }
                "DownArrow" { $pos = if ($pos -eq $runIndex) { 0 } else { $pos + 1 } }
                "Home"      { $pos = 0 }
                "End"       { $pos = $runIndex }
                "Q"         { return $null }
                "Escape"    { return $null }
                { $_ -eq "Enter" -or $_ -eq "Spacebar" } {
                    if ($pos -eq 0) {
                        $all = ($picked -ne $count)
                        foreach ($t in $tests) { $t.Selected = $all }
                    }
                    elseif ($pos -eq $runIndex) {
                        if ($picked -eq 0) {
                            $message = "${RED}Tick at least one test first$R"
                        } else {
                            return @($tests | Where-Object { $_.Selected } | ForEach-Object { $_.Name })
                        }
                    }
                    else {
                        $tests[$pos - 1].Selected = -not $tests[$pos - 1].Selected
                    }
                }
            }
        }
    }
    finally {
        [Console]::CursorVisible = $true
    }
}

function Invoke-Tests([string[]]$classes, [string]$what) {
    Write-Header

    Write-Host "  $B[1/3]$R Checking setup"
    if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
        Write-Host "        ${RED}x javac not found - install a JDK 17+ and add it to PATH$R"
        return 1
    }
    $tomcat = if ($env:CATALINA_HOME) { $env:CATALINA_HOME } else { Join-Path $root "..\.." }
    $tomcat = [IO.Path]::GetFullPath($tomcat)
    $servletApi = Join-Path $tomcat "lib\servlet-api.jar"
    if (-not (Test-Path $servletApi)) {
        Write-Host "        ${RED}x could not find $servletApi$R"
        Write-Host "        ${D}set CATALINA_HOME to your Tomcat folder and try again$R"
        return 1
    }
    Write-Host "        ${GRN}ok$R Tomcat   $D$tomcat$R"
    Write-Host "        ${GRN}ok$R Account  $D$($env:TEST_LOGIN_NAME) (live login tests)$R"

    $lib = "Test\lib"
    $cp = @(
        "Test\out", "WEB-INF\classes", $servletApi,
        "$lib\junit-platform-console-standalone-1.10.2.jar", "$lib\mockito-core-5.11.0.jar",
        "$lib\byte-buddy-1.14.12.jar", "$lib\byte-buddy-agent-1.14.12.jar", "$lib\objenesis-3.3.jar"
    ) -join ";"

    $sources = @(Get-ChildItem "Test\Back-End\*.java", "Test\Back-End\Test-Runner\*.java" | ForEach-Object { $_.FullName })
    Write-Host ""
    Write-Host "  $B[2/3]$R Compiling $(@(Get-ChildItem 'Test\Back-End\*.java').Count) test files"
    if (Test-Path "Test\out") { Remove-Item "Test\out" -Recurse -Force }
    New-Item -ItemType Directory "Test\out" | Out-Null
    $log = & cmd /c "javac -nowarn -encoding UTF-8 -d Test\out -cp `"$cp`" $($sources -join ' ') 2>&1"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "        ${RED}x compile failed:$R"
        Write-Host ""
        $log | ForEach-Object { Write-Host "    $_" }
        return 1
    }
    Write-Host "        ${GRN}ok$R compiled"

    Write-Host ""
    Write-Host "  $B[3/3]$R Running $what"

    $rows = 0; $cols = 100
    if ($interactive) {
        $rows = [Console]::WindowHeight
        $cols = [Console]::WindowWidth
    }
    $javaArgs = "-Xshare:off -Dtests.rows=$rows -Dtests.cols=$cols -cp `"$cp`" RunTests $($classes -join ' ')"
    $process = Start-Process java -ArgumentList $javaArgs -NoNewWindow -PassThru
    $null = $process.Handle
    $process.WaitForExit()
    Write-Host ""
    return $process.ExitCode
}

$oldEncoding = [Console]::OutputEncoding
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Push-Location $root
$result = 1

try {
    $tests = @(Get-TestClasses)

    if (-not $interactive) {
        if ($Only) {
            $result = Invoke-Tests @($Only) $Only
        } else {
            $result = Invoke-Tests @($tests | ForEach-Object { $_.Name }) "all $($tests.Count) test classes"
        }
    }
    else {
        $choice = Show-Picker $tests
        while ($null -ne $choice) {
            $what = if ($choice.Count -eq $tests.Count) { "all $($tests.Count) test classes" } else { "$($choice.Count) of $($tests.Count) test classes" }

            $again = $true
            while ($again) {
                Clear-Host
                $result = Invoke-Tests $choice $what
                Write-Host "  ${D}Enter$R run again    ${D}S$R select tests    ${D}Q$R quit"
                $next = $null
                while ($null -eq $next) {
                    $key = [Console]::ReadKey($true)
                    switch ($key.Key) {
                        "Enter"  { $next = "again" }
                        "S"      { $next = "select" }
                        "Q"      { $next = "quit" }
                        "Escape" { $next = "quit" }
                    }
                }
                $again = ($next -eq "again")
            }

            if ($next -eq "select") {
                $choice = Show-Picker $tests
            } else {
                $choice = $null
            }
        }
    }
}
finally {
    Pop-Location
    [Console]::OutputEncoding = $oldEncoding
    if ($interactive) { [Console]::CursorVisible = $true }
}

exit $result
