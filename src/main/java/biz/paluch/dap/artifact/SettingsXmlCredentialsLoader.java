/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package biz.paluch.dap.artifact;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

/**
 * Reads Maven {@code settings.xml} for the active project and returns a map of server-id to
 * {@link RepositoryCredentials}.
 * <p>
 * Settings files are resolved and merged in priority order (lowest first):
 * <ol>
 * <li>Global settings: {@code <maven_home>/conf/settings.xml}</li>
 * <li>User settings: path from {@link MavenUtil#resolveUserSettingsPath}, which honours IntelliJ's configured path and
 * falls back to {@code ~/.m2/settings.xml}</li>
 * </ol>
 * <p>
 * Maven-encrypted passwords (those enclosed in {@code {...}}) are decrypted using the same PBE algorithm as
 * {@code plexus-cipher}: SHA-256 key derivation with AES-128/CBC. The master password is read from
 * {@code ~/.m2/settings-security.xml} (or the location given by the {@code settings.security} system property).
 */
public class SettingsXmlCredentialsLoader {

	private static final Logger LOG = Logger.getInstance(SettingsXmlCredentialsLoader.class);

	/** Matches Maven-encrypted values: {@code {base64...}}. */
	private static final Pattern ENCRYPTED_VALUE = Pattern.compile("\\{[^}]+\\}");

	/** Passphrase Maven uses to encrypt the master password itself. */
	private static final String MASTER_PASSWORD_PASSPHRASE = "settings.security";

	private SettingsXmlCredentialsLoader() {}

	/**
	 * Loads credentials from the Maven settings files applicable to the given project.
	 *
	 * @param project the IntelliJ project
	 * @return map from server {@code <id>} to credentials; never {@code null}, may be empty
	 */
	public static Map<String, RepositoryCredentials> load(Project project) {

		MavenProjectsManager mavenManager = MavenProjectsManager.getInstance(project);
		if (mavenManager == null) {
			return Collections.emptyMap();
		}

		Map<String, RepositoryCredentials> result = new LinkedHashMap<>();

		// Global settings (lower priority) — <maven_home>/conf/settings.xml
		String mavenHome = mavenManager.getGeneralSettings().getMavenHome();
		File mavenHomeDir = MavenUtil.resolveMavenHomeDirectory(mavenHome);
		if (mavenHomeDir != null) {
			Path globalSettings = mavenHomeDir.toPath().resolve("conf/settings.xml");
			if (Files.isRegularFile(globalSettings)) {
				result.putAll(parseSettings(globalSettings));
			}
		}

		// User settings (higher priority) — resolved via IntelliJ Maven API, which handles
		// the configured override path, default ~/.m2/settings.xml, and remote EEL targets.
		String configuredUserSettings = mavenManager.getGeneralSettings().getUserSettingsFile();
		Path userSettings = MavenUtil.resolveUserSettingsPath(configuredUserSettings, project);
		if (userSettings != null && Files.isRegularFile(userSettings)) {
			result.putAll(parseSettings(userSettings));
		}

		return result;
	}

	private static Map<String, RepositoryCredentials> parseSettings(Path settingsPath) {

		Document doc;
		try (InputStream in = Files.newInputStream(settingsPath)) {
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
		} catch (Exception e) {
			LOG.debug("Could not parse Maven settings at " + settingsPath, e);
			return Collections.emptyMap();
		}

		Map<String, RepositoryCredentials> credentials = new LinkedHashMap<>();
		NodeList servers = doc.getElementsByTagName("server");

		for (int i = 0; i < servers.getLength(); i++) {
			Element server = (Element) servers.item(i);
			String id = firstChildText(server, "id");
			String username = firstChildText(server, "username");
			String rawPassword = firstChildText(server, "password");

			if (id == null || id.isBlank() || username == null || username.isBlank() || rawPassword == null
					|| rawPassword.isBlank()) {
				continue;
			}

			String password = decryptIfEncrypted(id, rawPassword.trim());
			if (password != null) {
				credentials.put(id.trim(), new RepositoryCredentials(username.trim(), password));
			}
		}

		return credentials;
	}

	// -------------------------------------------------------------------------
	// Maven password decryption
	// -------------------------------------------------------------------------

	/**
	 * Returns the plaintext password. Encrypted passwords (wrapped in {@code {...}}) are decrypted using the
	 * Maven/plexus-cipher PBE scheme; if decryption fails the entry is omitted. Plaintext passwords are returned as-is.
	 */
	@Nullable
	private static String decryptIfEncrypted(String serverId, String rawPassword) {

		if (!ENCRYPTED_VALUE.matcher(rawPassword).matches()) {
			return rawPassword;
		}

		String masterPassword = readMasterPassword();
		if (masterPassword == null) {
			LOG.debug("Skipping encrypted password for server '" + serverId
					+ "' — settings-security.xml not found or master password could not be decrypted");
			return null;
		}

		try {
			String inner = rawPassword.substring(1, rawPassword.length() - 1);
			return pbeDecrypt(inner, masterPassword);
		} catch (Exception e) {
			LOG.debug("Failed to decrypt password for Maven server '" + serverId + "'", e);
			return null;
		}
	}

	/**
	 * Reads and decrypts the master password from {@code settings-security.xml}.
	 * <p>
	 * The file location is taken from the {@code settings.security} system property, defaulting to
	 * {@code ~/.m2/settings-security.xml}.
	 */
	@Nullable
	private static String readMasterPassword() {

		String secFile = System.getProperty("settings.security",
				System.getProperty("user.home") + "/.m2/settings-security.xml");
		Path secPath = Paths.get(secFile);

		if (!Files.isRegularFile(secPath)) {
			return null;
		}

		Document doc;
		try (InputStream in = Files.newInputStream(secPath)) {
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
		} catch (Exception e) {
			LOG.debug("Could not parse settings-security.xml at " + secPath, e);
			return null;
		}

		String encryptedMaster = firstChildText(doc.getDocumentElement(), "master");
		if (encryptedMaster == null || !ENCRYPTED_VALUE.matcher(encryptedMaster.trim()).matches()) {
			return null;
		}

		try {
			String inner = encryptedMaster.trim();
			inner = inner.substring(1, inner.length() - 1);
			return pbeDecrypt(inner, MASTER_PASSWORD_PASSPHRASE);
		} catch (Exception e) {
			LOG.debug("Failed to decrypt master password from " + secPath, e);
			return null;
		}
	}

	/**
	 * Decrypts a Base64-encoded Maven/plexus-cipher ciphertext using the given passphrase.
	 * <p>
	 * Matches the {@code PBECipher} algorithm bundled with Maven 3 ({@code plexus-cipher 2.x}):
	 * <ul>
	 * <li>Key derivation: one round of SHA-256 over {@code passphrase || salt[0..7]}; first 16 bytes → AES-128 key, bytes
	 * 16–31 → CBC IV.</li>
	 * <li>Cipher: AES/CBC/PKCS5Padding.</li>
	 * <li>Wire format (after Base64 decode): {@code salt[8] | padlen[1] | ciphertext | padding[padlen]}.</li>
	 * </ul>
	 */
	private static String pbeDecrypt(String base64Cipher, String passphrase) throws Exception {

		byte[] decoded = Base64.getDecoder().decode(base64Cipher);

		byte[] salt = Arrays.copyOf(decoded, 8);
		int padlen = decoded[8] & 0xFF;
		int ciphertextLen = decoded.length - 9 - padlen;
		byte[] ciphertext = Arrays.copyOfRange(decoded, 9, 9 + ciphertextLen);

		// SHA-256(passphrase || salt) → 32 bytes; split into key (0..15) and IV (16..31)
		MessageDigest sha = MessageDigest.getInstance("SHA-256");
		sha.update(passphrase.getBytes(StandardCharsets.UTF_8));
		sha.update(salt);
		byte[] derived = sha.digest();

		SecretKeySpec key = new SecretKeySpec(Arrays.copyOf(derived, 16), "AES");
		IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(derived, 16, 32));

		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher.init(Cipher.DECRYPT_MODE, key, iv);

		return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
	}

	// -------------------------------------------------------------------------
	// XML helpers
	// -------------------------------------------------------------------------

	@Nullable
	private static String firstChildText(Element parent, String tagName) {
		NodeList nodes = parent.getElementsByTagName(tagName);
		if (nodes.getLength() == 0) {
			return null;
		}
		String text = nodes.item(0).getTextContent();
		return (text != null) ? text.trim() : null;
	}
}
