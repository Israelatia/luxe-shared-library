// luxelib.groovy

def call(body) {
    // Evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    
    // Define the shared library methods
    def lib = [
        buildDockerImage: this.&buildDockerImage,
        pushToRegistry: this.&pushToRegistry,
        runSecurityScan: this.&runSecurityScan,
        runTests: this.&runTests,
        runCodeQuality: this.&runCodeQuality,
        deployApplication: this.&deployApplication,
        notifySlack: this.&notifySlack
    ]
    
    // Return the library methods
    return lib
}
