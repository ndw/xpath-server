# This is a fairly naive Powershell script that constructs a classpath for
# running XPath server. The idea is that it puts jar files from the "extra"
# directory ahead of jar files from the "lib" directory. This should support
# overriding jars. And supports running steps that require extra libraries.

$cp = "$PSScriptRoot\xpath-server-@@VERSION@@.jar"

if (![System.IO.File]::Exists("$cp")) {
  Write-Host "XPath server script did not find the @@VERSION@@ distribution jar"
  Exit 1
}

Get-ChildItem "$PSScriptRoot\extra" -Filter *.jar |
ForEach-Object {
  $cp = "$cp;$PSScriptroot\lib\$_"
}

Get-ChildItem "$PSScriptRoot\lib" -Filter *.jar |
ForEach-Object {
  $cp = "$cp;$PSScriptroot\lib\$_"
}

# FIXME: should there be some attempt to look for $Env:JAVA_HOME here?

java -cp "$cp" com.nwalsh.xml.xpathserver.Main $args
