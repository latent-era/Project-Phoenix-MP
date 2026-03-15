param([string]$Path)
function BE32([byte[]]$b,[int]$o){ (($b[$o]-shl 24) -bor ($b[$o+1]-shl 16) -bor ($b[$o+2]-shl 8) -bor $b[$o+3]) }
function LE16([byte[]]$b,[int]$o){ ($b[$o] -bor ($b[$o+1]-shl 8)) }
$bytes=[IO.File]::ReadAllBytes($Path)
$offset=16
$idx=0
while($offset+24 -le $bytes.Length){
$incl=BE32 $bytes ($offset+4)
$dataOff=$offset+24
if($dataOff+$incl -gt $bytes.Length){ break }
if($incl -gt 0 -and $bytes[$dataOff] -eq 0x02 -and $incl -ge 10){
$acl=$dataOff+1
$conn=(LE16 $bytes $acl) -band 0x0FFF
$l2len=LE16 $bytes ($acl+4)
$cid=LE16 $bytes ($acl+6)
$att=$acl+8
if($conn -eq 65 -and $cid -eq 4 -and $l2len -ge 1){
$op=$bytes[$att]
$handle=$null
$valueStart=$null
$valueLen=0
$prepOffset=0
if($op -in 0x12,0x52,0x1B -and $l2len -ge 3){ $handle=LE16 $bytes ($att+1); $valueStart=$att+3; $valueLen=($acl+8+$l2len)-$valueStart }
elseif($op -in 0x16,0x17 -and $l2len -ge 5){ $handle=LE16 $bytes ($att+1); $prepOffset=LE16 $bytes ($att+3); $valueStart=$att+5; $valueLen=($acl+8+$l2len)-$valueStart }
elseif($op -in 0x18,0x19){ $valueStart=$att+1; $valueLen=($acl+8+$l2len)-$valueStart }
if(($handle -eq 0x0058) -or $op -in 0x18,0x19){
if($valueLen -lt 0){ $valueLen=0 }
$hex=""
if($valueLen -gt 0){ $hex=($bytes[$valueStart..([Math]::Min($valueStart+$valueLen-1,$dataOff+$incl-1))] | ForEach-Object { $_.ToString("X2") }) -join " " }
$extra=""
if($op -in 0x16,0x17){ $extra=(' prepOffset=0x{0:X4}' -f $prepOffset) }
$handleText = if($null -eq $handle){ '--' } else { '0x{0:X4}' -f $handle }
Write-Output ('idx={0} op=0x{1:X2} handle={2}{3} len={4} data={5}' -f $idx,$op,$handleText,$extra,$valueLen,$hex)
}
}
}
 $offset=$dataOff+$incl
$idx++
}
