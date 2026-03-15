param(
    [Parameter(Mandatory = $true)]
    [string]$Path,

    [int]$Limit = 200
)

function Read-BE32([byte[]]$bytes, [int]$offset) {
    return (($bytes[$offset] -shl 24) -bor ($bytes[$offset + 1] -shl 16) -bor ($bytes[$offset + 2] -shl 8) -bor $bytes[$offset + 3])
}

function Read-LE16([byte[]]$bytes, [int]$offset) {
    return ($bytes[$offset] -bor ($bytes[$offset + 1] -shl 8))
}

$bytes = [IO.File]::ReadAllBytes($Path)
$offset = 16
$index = 0
$results = New-Object System.Collections.Generic.List[object]

while ($offset + 24 -le $bytes.Length -and $results.Count -lt $Limit) {
    $includedLength = Read-BE32 $bytes ($offset + 4)
    $flags = Read-BE32 $bytes ($offset + 8)
    $recordOffset = $offset + 24
    if ($recordOffset + $includedLength -gt $bytes.Length) {
        break
    }

    if ($includedLength -gt 0 -and $bytes[$recordOffset] -eq 0x02 -and $includedLength -ge 10) {
        $aclOffset = $recordOffset + 1
        $connHandle = (Read-LE16 $bytes $aclOffset) -band 0x0FFF
        $l2capLength = Read-LE16 $bytes ($aclOffset + 4)
        $cid = Read-LE16 $bytes ($aclOffset + 6)

        if ($cid -eq 0x0004 -and $l2capLength -ge 1) {
            $attOffset = $aclOffset + 8
            $attOp = $bytes[$attOffset]

            if ($attOp -in 0x12, 0x52, 0x1B, 0x02, 0x03, 0x04, 0x05, 0x08, 0x09, 0x10, 0x11) {
                $handle = $null
                $valueStart = $attOffset + 1
                if ($attOp -in 0x12, 0x52, 0x1B) {
                    $handle = Read-LE16 $bytes ($attOffset + 1)
                    $valueStart = $attOffset + 3
                }

                $valueLength = ($aclOffset + 8 + $l2capLength) - $valueStart
                if ($valueLength -lt 0) {
                    $valueLength = 0
                }

                $take = [Math]::Min($valueLength, 24)
                $hex = ""
                if ($take -gt 0) {
                    $hex = ($bytes[$valueStart..($valueStart + $take - 1)] | ForEach-Object { $_.ToString("X2") }) -join " "
                }

                $results.Add([pscustomobject]@{
                    Index = $index
                    Flags = $flags
                    Conn = $connHandle
                    Op = ('0x{0:X2}' -f $attOp)
                    Handle = if ($null -eq $handle) { "" } else { '0x{0:X4}' -f $handle }
                    Length = $valueLength
                    Data = $hex
                })
            }
        }
    }

    $offset = $recordOffset + $includedLength
    $index++
}

$results | Format-Table -AutoSize
