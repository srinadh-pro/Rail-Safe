param(
    [string] $EnvFile = (Join-Path $PSScriptRoot 'railsafe.env.local')
)

if (-not (Test-Path $EnvFile)) {
    throw "Missing $EnvFile. Copy deployment/railsafe.env.example to railsafe.env.local and fill in the real values."
}

Get-Content $EnvFile |
    Where-Object { $_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$' } |
    ForEach-Object {
        $name = $matches[1]
        $value = $matches[2].Trim()
        if ($value.StartsWith('"') -and $value.EndsWith('"')) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        Set-Item -Path "env:$name" -Value $value
    }

$jar = Join-Path (Split-Path $PSScriptRoot -Parent) 'target\railsafe-1.0.0.jar'
if (-not (Test-Path $jar)) {
    throw "Missing $jar. Run .\mvnw.cmd -q -DskipTests package first."
}

java -jar $jar
