package freemail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;

import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import java.security.SecureRandom;

import freemail.fcp.HighLevelFCPClient;
import freemail.fcp.SSKKeyPair;
import freemail.util.PropsFile;

public class AccountManager {
	public static final String DATADIR = "data";
	// this really doesn't matter a great deal
	public static final String NIMDIR = "nim";
	
	private static final String ACCOUNT_FILE = "accprops";
	private static final int RTS_KEY_LENGTH = 32;
	
	private static final int ASYM_KEY_MODULUS_LENGTH = 4096;
	private static final BigInteger ASYM_KEY_EXPONENT = new BigInteger("17", 10);
	private static final int ASYM_KEY_CERTAINTY = 80;
	

	public static void Create(String username) throws IOException {
		File datadir = new File(DATADIR);
		if (!datadir.exists()) {
			if (!datadir.mkdir()) throw new IOException("Failed to create data directory");
		}
		
		File accountdir = new File(DATADIR, username);
		if (!accountdir.mkdir()) throw new IOException("Failed to create directory "+username+" in "+DATADIR);
	}
	
	public static void setupNIM(String username) throws IOException {
		File accountdir = new File(DATADIR, username);
		
		File contacts_dir = new File(accountdir, SingleAccountWatcher.CONTACTS_DIR);
		if (!contacts_dir.exists()) {
			if (!contacts_dir.mkdir()) throw new IOException("Failed to create contacts directory");
		}
		
		File nimdir = new File(contacts_dir, NIMDIR);
		if (!nimdir.exists()) {
			if (!nimdir.mkdir()) throw new IOException("Failed to create nim directory");
		}
		
		File keyfile = new File(nimdir, Contact.KEYFILE);
		PrintWriter pw = new PrintWriter(new FileOutputStream(keyfile));
		
		pw.println(MessageSender.NIM_KEY_PREFIX + username + "-");
		
		pw.close();
	}
	
	public static void ChangePassword(String username, String newpassword) throws Exception {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException alge) {
			throw new Exception("No MD5 implementation available - sorry, Freemail cannot work!");
		}
		
		File accountdir = new File(DATADIR, username);
		if (!accountdir.exists()) {
			throw new Exception("No such account - "+username+".");
		}
		
		PropsFile accfile = getAccountFile(accountdir);
		
		byte[] md5passwd = md.digest(newpassword.getBytes());
		String strmd5 = bytestoHex(md5passwd);
		
		accfile.put("md5passwd", strmd5);
	}
	
	public static PropsFile getAccountFile(File accdir) {
		PropsFile accfile = new PropsFile(new File(accdir, ACCOUNT_FILE));
		
		if (!accfile.exists()) {
			initAccFile(accfile);
		}
		
		return accfile;
	}
	
	private static void initAccFile(PropsFile accfile) {
		try {
			System.out.println("Generating mailsite keys...");
			HighLevelFCPClient fcpcli = new HighLevelFCPClient();
			
			SSKKeyPair keypair = fcpcli.makeSSK();
			
			// write private key
			if (!accfile.put("mailsite.privkey", keypair.privkey+"mailsite")) {
				throw new IOException("Unable to write account file");
			}
			
			// write public key
			if (!accfile.put("mailsite.pubkey", keypair.pubkey+"mailsite")) {
				throw new IOException("Unable to write account file");
			}
			
			// initialise RTS/CTS KSK
			Random rnd = new Random();
			String rtskey = new String();
			
			int i;
			for (i = 0; i < RTS_KEY_LENGTH; i++) {
				rtskey += (char)(rnd.nextInt(25) + (int)'a');
			}
			
			if (!accfile.put("rtskey", rtskey)) {
				throw new IOException("Unable to write account file");
			}
			
			System.out.println("Mailsite keys generated.");
		} catch (IOException ioe) {
			System.out.println("Couldn't create mailsite key file! "+ioe.getMessage());
		}
		
		// generate an RSA keypair
		System.out.println("Generating cryptographic keypair (this could take a few minutes)...");
		
		SecureRandom rand = new SecureRandom();

		RSAKeyGenerationParameters kparams = new RSAKeyGenerationParameters(ASYM_KEY_EXPONENT, rand, ASYM_KEY_MODULUS_LENGTH, ASYM_KEY_CERTAINTY);

		RSAKeyPairGenerator kpg = new RSAKeyPairGenerator();
		kpg.init(kparams);
		
		AsymmetricCipherKeyPair keypair = kpg.generateKeyPair();
		RSAKeyParameters pub = (RSAKeyParameters) keypair.getPublic();
		RSAKeyParameters priv = (RSAKeyParameters) keypair.getPrivate();

		accfile.put("asymkey.modulus", pub.getModulus().toString());
		accfile.put("asymkey.pubexponent", pub.getExponent().toString());
		accfile.put("asymkey.privexponent", priv.getExponent().toString());
		
		System.out.println("Account creation completed.");
	}
	
	public static boolean authenticate(String username, String password) {
		if (!validate_username(username)) return false;
		
		//String sep = System.getProperty("file.separator");
		
		File accountdir = new File(DATADIR, username);
		if (!accountdir.exists()) {
			return false;
		}
		PropsFile accfile = getAccountFile(accountdir);
		
		String realmd5str = accfile.get("md5passwd");
		if (realmd5str == null) return false;
		
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException alge) {
			System.out.println("No MD5 implementation available - logins will not work!");
			return false;
		}
		byte[] givenmd5 = md.digest(password.getBytes());
		
		String givenmd5str = bytestoHex(givenmd5);
		
		if (realmd5str.equals(givenmd5str)) {
			return true;
		}
		return false;
	}
	
	private static boolean validate_username(String username) {
		if (username.matches("[\\w_]*")) return true;
		return false;
	}
	
	public static String bytestoHex(byte[] bytes) {
		String retval = new String("");
		
		for (int i = 0; i < bytes.length; i++) {
			String b = Integer.toHexString((int)(bytes[i] & 0xFF));
			if (b.length() < 2) {
				b = "0" + b;
			}
			retval += b;
		}
		return retval;
	}
}
