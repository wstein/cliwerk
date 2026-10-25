# Usage: <powershell|pwsh> -NoProfile -File verify-completions.ps1 <dir with the golden .ps1 files>
#
# Loads picocli's generated PowerShell completion scripts (the golden files in src/test/resources) into this
# PowerShell session and asks PowerShell what it would complete (TabExpansion2, the engine behind the TAB key and
# CompleteInput). Runs on Windows PowerShell 5.1 and PowerShell 7+, so it sticks to syntax that exists in both.
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Dir
)

$ErrorActionPreference = 'Stop'
$script:failures = 0
$script:checks = 0
$flavor = 'PowerShell ' + $PSVersionTable.PSVersion + ' (' + $PSVersionTable.PSEdition + ')'
Write-Host "Verifying picocli completion scripts with $flavor"

# Fail "<message>"   reports a failed check; on GitHub Actions also as an annotation, so it is visible without the log
function Fail([string]$message) {
    Write-Host "FAIL: $message"
    if ($env:GITHUB_ACTIONS -eq 'true') {
        $escaped = $message.Replace('%', '%25').Replace("`r", '%0D').Replace("`n", '%0A')
        Write-Host "::error title=$flavor::$escaped"
    }
    $script:failures++
}

# Load "<file>"   loads a golden file the way a user would: the text of the script is evaluated in this session.
# The golden files are java.util.Formatter templates: %1$s is the picocli version and %% a literal percent sign.
function Load([string]$file) {
    $path = Join-Path $Dir $file
    $bytes = [System.IO.File]::ReadAllBytes($path)
    foreach ($b in $bytes) {
        if ($b -gt 126 -or ($b -lt 32 -and $b -ne 10 -and $b -ne 13)) { Fail "$file contains the non-ASCII or control byte $b"; break }
    }
    $text = [System.Text.Encoding]::ASCII.GetString($bytes).Replace('%1$s', 'test').Replace('%%', '%')
    # the PowerShell parser must accept the script, whatever the PowerShell version
    $parseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseInput($text, [ref]$null, [ref]$parseErrors)
    foreach ($e in $parseErrors) { Fail "$file line $($e.Extent.StartLineNumber): $($e.Message)" }
    try {
        . ([scriptblock]::Create($text))
    } catch {
        Fail "$file does not load: $($_.Exception.Message)"
    }
}

# Complete "<command line>"   everything PowerShell offers when TAB is pressed at the end of the line
function Complete([string]$line) {
    $result = TabExpansion2 -inputScript $line -cursorColumn $line.Length
    return @($result.CompletionMatches)
}

function Texts($items) { return @($items | ForEach-Object { $_.ListItemText }) }

# ExpectHas "<command line>" <candidate...>   every candidate must be offered
function ExpectHas([string]$line, [string[]]$wanted) {
    $script:checks++
    $offered = Texts (Complete $line)
    foreach ($want in $wanted) {
        if (-not ($offered -ccontains $want)) { Fail "'$line' should offer '$want', but offered: $($offered -join ' ')" }
    }
}

# ExpectLacks "<command line>" <candidate...>   none of the candidates may be offered
function ExpectLacks([string]$line, [string[]]$unwanted) {
    $script:checks++
    $offered = Texts (Complete $line)
    foreach ($no in $unwanted) {
        if ($offered -ccontains $no) { Fail "'$line' should not offer '$no', but offered: $($offered -join ' ')" }
    }
}

# ExpectFile "<command line>" <file name>   the file name completion of PowerShell (./name or .\name) kicked in
function ExpectFile([string]$line, [string]$name) {
    $script:checks++
    $offered = Texts (Complete $line)
    if (-not (@($offered | Where-Object { $_ -like "*$name" }).Count -gt 0)) { Fail "'$line' should offer the file '$name', but offered: $($offered -join ' ')" }
}

# ExpectEquals "<what>" <actual> <expected>
function ExpectEquals([string]$what, $actual, $expected) {
    $script:checks++
    if ($actual -cne $expected) { Fail "$what should be '$expected', but is '$actual'" }
}

Load 'basic.ps1'
Load 'picocompletion-demo_completion.ps1'
Load 'powershell-features.ps1'

# subcommands and options of the root command
ExpectHas   'picocompletion-demo ' sub1, sub2
ExpectHas   'picocompletion-demo -' '--version', '--help'
# once drilled into a subcommand, the parent's options are no longer suggested
ExpectHas   'picocompletion-demo sub1 --' '--num', '--str', '--candidates'
ExpectLacks 'picocompletion-demo sub1 --' '--version', '--help'
# nested subcommands
ExpectHas   'picocompletion-demo sub2 ' subsub1, subsub2, subsub3
ExpectHas   'picocompletion-demo sub2 subsub2 -' '--timeUnit', '--timeout'
ExpectLacks 'picocompletion-demo sub2 subsub2 -' '--num2', '--host'
# prefixes narrow the candidates down
ExpectHas   'picocompletion-demo sub2 subsub2 --timeU' '--timeUnit'
ExpectLacks 'picocompletion-demo sub2 subsub2 --timeU' '--timeout'
# option values: only the candidates are offered
ExpectHas   'picocompletion-demo sub1 --candidates ' aaa, bbb, ccc
ExpectLacks 'picocompletion-demo sub1 --candidates ' sub2, '--num'
ExpectHas   'picocompletion-demo sub2 --thread-state ' NEW, RUNNABLE, TIMED_WAITING
# a second command, loaded from another file
ExpectHas   'basicExample --timeUnit ' NANOSECONDS, DAYS
ExpectHas   'basicExample -u ' NANOSECONDS, DAYS
ExpectHas   'basicExample --timeUnit=D' '--timeUnit=DAYS'
ExpectLacks 'basicExample --timeUnit=D' '--timeUnit=SECONDS'

# the psfeatures checks run in a fresh, private temporary directory that holds one file (no fixed /tmp paths)
$olddir = Get-Location
$workdir = Join-Path ([System.IO.Path]::GetTempPath()) ('picocli-ps-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $workdir | Out-Null
try {
    Set-Location $workdir
    Set-Content -Path 'picocli-ps-file.txt' -Value 'x'

    # negatable options: the negative name is offered too, and a prefix narrows the options down
    ExpectHas   'psfeatures --' '--color', '--no-color', '--shared', '--pick'
    ExpectHas   'psfeatures --no' '--no-color'
    ExpectLacks 'psfeatures --no' '--color'
    ExpectHas   'psfeatures -' '-c', '--color', '-u', '--unit'
    # hidden options are not offered, and neither are options when no dash has been typed
    ExpectLacks 'psfeatures -' '--secret'
    ExpectLacks 'psfeatures ' '--color'
    # an option name that does not start with a dash
    ExpectHas   'psfeatures /' /win
    # inherited options (scope = INHERIT) show up in the subcommand, the parent's own options do not
    ExpectHas   'psfeatures child --' '--own', '--shared'
    ExpectLacks 'psfeatures child --' '--color', '--pick'
    # aliases are offered, and walked like the subcommand itself
    ExpectHas   'psfeatures ' child, kid
    ExpectHas   'psfeatures kid --' '--own'
    ExpectLacks 'psfeatures kid --' '--color'
    ExpectHas   'psfeatures ki' kid
    ExpectLacks 'psfeatures ki' child
    # enum constants
    ExpectHas   'psfeatures --unit ' SECONDS, DAYS
    ExpectHas   'psfeatures -u M' MICROSECONDS, MINUTES
    ExpectLacks 'psfeatures -u M' SECONDS, DAYS
    ExpectHas   'psfeatures --unit=S' '--unit=SECONDS'
    # a clustered short option: -c is a flag, -u takes the next word as its value
    ExpectHas   'psfeatures -cu ' SECONDS, DAYS
    ExpectLacks 'psfeatures -cu ' child
    # candidates with spaces, quotes, backticks, dollar signs and non-ASCII characters are each offered as ONE completion
    $cafe = 'caf' + [char]0x00E9
    $awkward = @('plain', 'with space', "it's", '$(Get-Date)', 'a`b', 'say "hi"', $cafe, 'semi;colon')
    ExpectHas   'psfeatures --pick ' $awkward
    ExpectHas   'psfeatures --pick w' 'with space'
    ExpectLacks 'psfeatures --pick w' plain, child
    ExpectHas   'psfeatures --pick=w' '--pick=with space'
    # ... and the text that is inserted evaluates to exactly the candidate, without running anything
    foreach ($candidate in (Complete 'psfeatures --pick ')) {
        $script:checks++
        $value = & ([scriptblock]::Create('Write-Output ' + $candidate.CompletionText))
        if ($value -cne $candidate.ListItemText) {
            Fail "the completion text '$($candidate.CompletionText)' evaluates to '$value', not to '$($candidate.ListItemText)'"
        }
    }
    # tooltips carry the descriptions
    $tips = @{}
    foreach ($m in (Complete 'psfeatures --')) { $tips[$m.ListItemText] = $m.ToolTip }
    ExpectEquals 'the tooltip of --color' $tips['--color'] 'Use colors'
    ExpectEquals 'the tooltip of --no-color' $tips['--no-color'] 'Negates --color: Use colors'
    ExpectEquals 'the tooltip of --required' $tips['--required'] 'value required'
    # a required value (arity 1) is the next word, so subcommands are not suggested ...
    ExpectLacks 'psfeatures --required ' child, kid
    # ... but file names are, because nothing is known about the value
    ExpectFile  'psfeatures --required ' 'picocli-ps-file.txt'
    # an optional value (arity 0..1) may be omitted, so subcommands are still suggested ...
    ExpectHas   'psfeatures --optional ' child, kid
    ExpectHas   'psfeatures --mode ' child, kid
    # ... and the candidates are offered as well, as a separate word and after --mode=
    ExpectHas   'psfeatures --mode ' plain, 'with space'
    ExpectHas   'psfeatures --mode=' '--mode=plain', '--mode=with space'
    # a candidate that was typed after an optional value is that value: the subcommands are still next
    ExpectHas   'psfeatures --mode plain ' child
    # positional parameters: the candidates of the first one are offered next to the subcommands, once
    ExpectHas   'psfeatures ' child, plain, 'with space'
    ExpectHas   'psfeatures --color ' child, plain
    ExpectLacks 'psfeatures plain ' 'with space'
    ExpectLacks 'psfeatures child ' plain
    # after -- everything is a positional parameter
    ExpectLacks 'psfeatures -- ' child, kid
    ExpectHas   'psfeatures -- ' plain
    # candidates are never offered twice
    $texts = Texts (Complete 'psfeatures --mode ')
    ExpectEquals 'the number of distinct candidates' @($texts | Sort-Object -Unique).Count $texts.Count

    # loading a script again replaces the earlier registration instead of adding to it
    Load 'powershell-features.ps1'
    $texts = Texts (Complete 'psfeatures ')
    ExpectEquals 'the number of candidates after loading the script twice' $texts.Count (@($texts | Sort-Object -Unique).Count)
    # the .exe name that Windows users type is completed as well
    ExpectHas   'psfeatures.exe --' '--color', '--no-color'
} finally {
    Set-Location $olddir
    Remove-Item -Recurse -Force $workdir
}

if ($script:failures -gt 0) {
    Write-Host "$($script:failures) of $($script:checks) check(s) failed"
    exit 1
}
Write-Host "all $($script:checks) PowerShell completion checks passed"
