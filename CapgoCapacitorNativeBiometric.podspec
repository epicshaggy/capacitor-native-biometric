
  Pod::Spec.new do |s|
    s.name = 'CapgoCapacitorNativeBiometric'
    s.version = '7.1.13'
    s.summary = 'This plugin gives access to the native biometric apis for android and iOS'
    s.license = 'MIT'
    s.homepage = 'https://github.com/Cap-go/capacitor-native-biometric'
    s.author = 'Martin Donadieu'
    s.source = { :git => 'https://github.com/Cap-go/capacitor-native-biometric', :tag => s.version.to_s }
    s.source_files = 'ios/Sources/**/*.{swift,h,m,c,cc,mm,cpp}'
    s.ios.deployment_target = '15.0'
    s.dependency 'Capacitor'
  end
