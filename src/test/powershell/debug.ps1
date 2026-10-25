$script:log = New-Object System.Collections.ArrayList
Register-ArgumentCompleter -Native -CommandName 'dbg' -ScriptBlock {
    param($wordToComplete, $commandAst, $cursorPosition)
    $s = "word=[$wordToComplete] cursor=$cursorPosition astText=[$($commandAst.Extent.Text)] astStart=$($commandAst.Extent.StartOffset) elems="
    foreach ($e in $commandAst.CommandElements) { $s += "{" + $e.GetType().Name + ":" + $e.Extent.Text + "@" + $e.Extent.StartOffset + "-" + $e.Extent.EndOffset + "}" }
    [void]$script:log.Add($s)
    [System.Management.Automation.CompletionResult]::new('x', 'x', 'ParameterValue', 'x')
}
$out = New-Object System.Collections.ArrayList
foreach ($line in @('dbg ', 'dbg -', 'dbg --', 'dbg --a', 'dbg --a=', 'dbg -x ', 'dbg a b')) {
    $r = TabExpansion2 -inputScript $line -cursorColumn $line.Length
    [void]$out.Add("[$line] => " + (($r.CompletionMatches | ForEach-Object { $_.CompletionText }) -join ','))
}
$msg = (($script:log + $out) -join "`n").Replace('%', '%25').Replace("`r", '%0D').Replace("`n", '%0A')
Write-Host "::notice title=debug::$msg"
