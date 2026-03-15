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
if($conn -eq 65 -and $cid -eq 4 -and $l2len -ge 3){
$att=$acl+8
$op=$bytes[$att]
if($op -in 0x12,0x52,0x1B){
$handle=LE16 $bytes ($att+1)
if($handle -eq 0x0058){
$valueStart=$att+3
$valueLen=($acl+8+$l2len)-$valueStart
if($valueLen -lt 0){ $valueLen=0 }
$hex=""
if($valueLen -gt 0){ $hex=($bytes[$valueStart..($valueStart+$valueLen-1)] | ForEach-Object { $_.ToString("X2") }) -join " " }
Write-Output ('idx={0} op=0x{1:X2} len={2} data={3}' -f $idx,$op,$valueLen,$hex)
}
}
}
}
 $offset=$dataOff+$incl
$idx++
}
