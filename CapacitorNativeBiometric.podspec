
  Pod::Spec.new do |s|
    s.name = 'CapacitorNativeBiometric'
    s.version = '0.0.1'
    s.summary = 'This plugin gives access to the native biometric apis for android and iOS'
    s.license = 'MIT'
    s.homepage = 'https://github.com/epicshaggy/capacitor-native-biometric'
    s.author = 'Jose Martinez'
    s.source = { :git => 'https://github.com/epicshaggy/capacitor-native-biometric', :tag => s.version.to_s }
    s.source_files = 'ios/Plugin/**/*.{swift,h,m,c,cc,mm,cpp}'
    s.ios.deployment_target  = '12.0'
    s.dependency 'Capacitor'
  end
