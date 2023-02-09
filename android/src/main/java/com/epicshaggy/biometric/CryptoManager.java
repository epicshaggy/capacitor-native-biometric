package com.epicshaggy.biometric;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class CryptoManager {

    private KeyStore keyStore;
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";

    private static final String NATIVE_BIOMETRIC_SHARED_PREFERENCES = "NativeBiometricSharedPreferences";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String RSA_MODE = "RSA/ECB/PKCS1Padding";
    private static final String AES_MODE = "AES/ECB/PKCS7Padding";
    private static final String ENCRYPTED_KEY = "NativeBiometricKey";


    public void saveCredentials(Credentials credentials, Cipher cipher, Context context) throws GeneralSecurityException {
        if (credentials != null && credentials.username != null && credentials.password != null && credentials.server != null) {
            SharedPreferences.Editor editor = context.getSharedPreferences(NATIVE_BIOMETRIC_SHARED_PREFERENCES + "_" + credentials.server, Context.MODE_PRIVATE).edit();
            editor.putString(USERNAME_KEY, encryptString(credentials.username, cipher));
            editor.putString(PASSWORD_KEY, encryptString(credentials.password, cipher));
            editor.apply();
        } else {
            throw new GeneralSecurityException("NULL credentials");
        }
    }

    public void deleteCredentials(String server, Context context) throws GeneralSecurityException {
        if (server != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(NATIVE_BIOMETRIC_SHARED_PREFERENCES + "_" + server);
            } else {
                SharedPreferences.Editor editor = context.getSharedPreferences(NATIVE_BIOMETRIC_SHARED_PREFERENCES + "_" + server, Context.MODE_PRIVATE).edit();
                editor.clear();
                editor.apply();
            }
        } else {
            throw new GeneralSecurityException("NULL server");
        }
    }

    public Credentials getCredentials(String server, Cipher cipher, Context context) throws GeneralSecurityException {
        SharedPreferences sharedPreferences = context.getSharedPreferences(NATIVE_BIOMETRIC_SHARED_PREFERENCES + "_" + server, Context.MODE_PRIVATE);
        String encryptedUsername = sharedPreferences.getString(USERNAME_KEY, null);
        String encryptedPassword = sharedPreferences.getString(PASSWORD_KEY, null);
        return new Credentials(
                decryptString(encryptedUsername, cipher),
                decryptString(encryptedPassword, cipher),
                server
        );
    }

    private String encryptString(String stringToEncrypt, Cipher cipher) throws GeneralSecurityException {
        byte[] encodedBytes = cipher.doFinal(stringToEncrypt.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encodedBytes, Base64.DEFAULT);
    }

    private String decryptString(String stringToDecrypt, Cipher cipher) throws GeneralSecurityException {
        byte[] encryptedData = Base64.decode(stringToDecrypt, Base64.DEFAULT);
        byte[] decryptedData = cipher.doFinal(encryptedData);
        return new String(decryptedData, StandardCharsets.UTF_8);
    }

    Cipher getCipher() throws NoSuchPaddingException, NoSuchAlgorithmException, NoSuchProviderException {
        Cipher cipher;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cipher = Cipher.getInstance(TRANSFORMATION);
        } else {
            cipher = Cipher.getInstance(AES_MODE, "BC");
        }

        return cipher;
    }

    Key getSecretKey(String server, Context context) throws GeneralSecurityException, IOException {
        KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) getKeyStore().getEntry(server, null);
        if (secretKeyEntry != null) {
            return secretKeyEntry.getSecretKey();
        }
        return generateKey(server, context);
    }


    private Key generateKey(String server, Context context) throws GeneralSecurityException, IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(server, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT);
            builder
//                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
//                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(true)
                    .setRandomizedEncryptionRequired(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(true);
            }

            generator.init(builder.build());
            return generator.generateKey();
        } else {
            return getAESKey(server, context);
        }
    }

    private KeyStore getKeyStore() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        if (keyStore == null) {
            keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);
        }
        return keyStore;
    }

    private Key getAESKey(String server, Context context) throws CertificateException, NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, KeyStoreException, NoSuchProviderException, UnrecoverableEntryException, IOException, InvalidAlgorithmParameterException {
        SharedPreferences sharedPreferences = context.getSharedPreferences("", Context.MODE_PRIVATE);
        String encryptedKeyB64 = sharedPreferences.getString(ENCRYPTED_KEY, null);
        if (encryptedKeyB64 == null) {
            byte[] key = new byte[16];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(key);
            byte[] encryptedKey = rsaEncrypt(key, server, context);
            encryptedKeyB64 = Base64.encodeToString(encryptedKey, Base64.DEFAULT);
            SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.putString(ENCRYPTED_KEY, encryptedKeyB64);
            edit.apply();
            return new SecretKeySpec(key, "AES");
        } else {
            byte[] encryptedKey = Base64.decode(encryptedKeyB64, Base64.DEFAULT);
            byte[] key = rsaDecrypt(encryptedKey, server, context);
            return new SecretKeySpec(key, "AES");
        }
    }

    private KeyStore.PrivateKeyEntry getPrivateKeyEntry(String server, Context context) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, CertificateException, KeyStoreException, IOException, UnrecoverableEntryException {
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) getKeyStore().getEntry(server, null);

        if (privateKeyEntry == null) {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE);
            keyPairGenerator.initialize(new KeyPairGeneratorSpec.Builder(context)
                    .setAlias(server)
                    .build());
            keyPairGenerator.generateKeyPair();
        }

        return privateKeyEntry;
    }

    private byte[] rsaEncrypt(byte[] secret, String server, Context context) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, UnrecoverableEntryException, NoSuchProviderException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
        KeyStore.PrivateKeyEntry privateKeyEntry = getPrivateKeyEntry(server, context);
        // Encrypt the text
        Cipher inputCipher = Cipher.getInstance(RSA_MODE, "AndroidOpenSSL");
        inputCipher.init(Cipher.ENCRYPT_MODE, privateKeyEntry.getCertificate().getPublicKey());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, inputCipher);
        cipherOutputStream.write(secret);
        cipherOutputStream.close();

        byte[] vals = outputStream.toByteArray();
        return vals;
    }

    private byte[] rsaDecrypt(byte[] encrypted, String server, Context context) throws UnrecoverableEntryException, NoSuchAlgorithmException, KeyStoreException, NoSuchProviderException, NoSuchPaddingException, InvalidKeyException, IOException, CertificateException, InvalidAlgorithmParameterException {
        KeyStore.PrivateKeyEntry privateKeyEntry = getPrivateKeyEntry(server, context);
        Cipher output = Cipher.getInstance(RSA_MODE, "AndroidOpenSSL");
        output.init(Cipher.DECRYPT_MODE, privateKeyEntry.getPrivateKey());
        CipherInputStream cipherInputStream = new CipherInputStream(
                new ByteArrayInputStream(encrypted), output);
        ArrayList<Byte> values = new ArrayList<>();
        int nextByte;
        while ((nextByte = cipherInputStream.read()) != -1) {
            values.add((byte) nextByte);
        }

        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = values.get(i).byteValue();
        }
        return bytes;
    }
}
