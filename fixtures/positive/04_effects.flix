eff Console {
    def printStr(msg: String): Unit
}

def logMessage(): Unit \ Console = {
    do Console.printStr("Hello World")
}

def main(): Unit = {
    try {
        logMessage()
    } with Console {
        def printStr(msg) = ()
    }
}
