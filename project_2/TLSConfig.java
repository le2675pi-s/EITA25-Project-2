import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

public final class TLSConfig {
	public static SSLContext create(String keystore, String keystorePassword, String truststore, String truststorePassword) throws Exception {
		try { // set up key manager to perform server authentication
			KeyStore ks = KeyStore.getInstance("JKS");
			KeyStore ts = KeyStore.getInstance("JKS");
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			SSLContext ctx = SSLContext.getInstance("TLSv1.2");
			// keystore password (storepass)
			ks.load(new FileInputStream(keystore), keystorePassword.toCharArray());  
			// truststore password (storepass)
			ts.load(new FileInputStream(truststore), truststorePassword.toCharArray()); 
			kmf.init(ks, keystorePassword.toCharArray()); // certificate password (keypass)
			tmf.init(ts);  // possible to use keystore as truststore here
			ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
			return ctx;
		} catch (Exception e) {
			throw e;
		}
	}

	public static String getSubject(SSLSocket socket) throws SSLPeerUnverifiedException {
		SSLSession session = socket.getSession();
		Certificate[] cert = session.getPeerCertificates();
		X509Certificate cert0 = (X509Certificate) cert[0];
		String subject = cert0.getSubjectX500Principal().getName();
		return subject;
	}
}
