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
package biz.paluch.dap;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionOption;
import biz.paluch.dap.state.Artifact;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.Property;

import java.util.List;

import javax.swing.Icon;

import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;

/**
 * Gutter line marker that indicates an outdated Maven dependency or plugin version in a {@code pom.xml}.
 * <p>
 * The marker appears on the line of the version value — either a literal {@code <version>} tag inside a
 * {@code <dependency>} or {@code <plugin>}, or a {@code <properties>} child tag whose name maps to a known artifact in
 * the cache. The icon reflects the highest available upgrade tier: patch, minor, or major.
 * <p>
 * Only the in-memory {@link Cache} is consulted — no network I/O is performed — so this is safe to call on the EDT.
 * Markers appear only after the user has run a dependency check that populated the cache.
 * <p>
 * Clicking the gutter icon invokes the <em>Update Maven Plugins and Dependencies</em> action.
 */
public class OutdatedVersionLineMarkerProvider implements LineMarkerProvider {

	@Override
	public @Nullable LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {

		if (element.getNode().getElementType() != XmlTokenType.XML_DATA_CHARACTERS) {
			return null;
		}

		PsiFile file = element.getContainingFile();
		if (!(file instanceof XmlFile xmlFile) || !MavenUtils.isMavenPomFile(xmlFile)) {
			return null;
		}

		Project project = element.getProject();
		Cache cache = DependencyAssistantService.getInstance(project).getCache();

		// Inline <version> inside <dependency> or <plugin>
		XmlTag versionTag = XmlUtil.getVersionTag(element);
		if (versionTag != null) {
			return buildVersionTagMarker(element, versionTag, cache);
		}

		// <properties> child tag that controls a dependency version
		XmlTag propertyTag = XmlUtil.getPropertyTag(element);
		if (propertyTag != null) {
			return buildPropertyTagMarker(element, propertyTag, cache);
		}

		return null;
	}

	private @Nullable LineMarkerInfo<?> buildVersionTagMarker(PsiElement element, XmlTag versionTag, Cache cache) {

		XmlTag parentTag = versionTag.getParentTag();
		String groupId = parentTag.getSubTagText("groupId");
		String artifactId = parentTag.getSubTagText("artifactId");
		if (!StringUtils.hasText(groupId) || !StringUtils.hasText(artifactId)) {
			return null;
		}

		String version = versionTag.getValue().getText().trim();
		if (version.contains("${")) {
			return null;
		}

		ArtifactVersion currentVersion;
		try {
			currentVersion = biz.paluch.dap.artifact.ArtifactVersion.of(version);
		} catch (Exception e) {
			return null;
		}

		List<VersionOption> options = cache.getVersionOptions(ArtifactId.of(groupId, artifactId), false);
		if (options.isEmpty()) {
			return null;
		}

		return buildMarker(element, currentVersion, options);
	}

	private @Nullable LineMarkerInfo<?> buildPropertyTagMarker(PsiElement element, XmlTag propertyTag, Cache cache) {

		String propertyName = propertyTag.getLocalName();
		Property property = cache.getProperty(propertyName);
		if (property == null || property.artifacts().isEmpty()) {
			return null;
		}

		String currentVersionText = propertyTag.getValue().getText().trim();
		if (currentVersionText.contains("${")) {
			return null;
		}

		ArtifactVersion currentVersion;
		try {
			currentVersion = biz.paluch.dap.artifact.ArtifactVersion.of(currentVersionText);
		} catch (Exception e) {
			return null;
		}

		// Use the first mapped artifact for the version lookup
		Artifact firstArtifact = property.artifacts().iterator().next();
		List<VersionOption> options = cache.getVersionOptions(firstArtifact.toArtifactId(), false);
		if (options.isEmpty()) {
			return null;
		}

		return buildMarker(element, currentVersion, options);
	}

	private @Nullable LineMarkerInfo<?> buildMarker(PsiElement element, ArtifactVersion current,
			List<VersionOption> options) {

		VersionOption major = UpgradeStrategy.MAJOR.select(current, options);
		VersionOption minor = UpgradeStrategy.MINOR.select(current, options);
		VersionOption patch = UpgradeStrategy.PATCH.select(current, options);

		if (major == null && minor == null && patch == null) {
			return null;
		}

		VersionAge age;
		VersionOption bestOption;
		if (major != null) {
			age = VersionAge.NEWER_MAJOR;
			bestOption = major;
		} else if (minor != null) {
			age = VersionAge.NEWER_MINOR;
			bestOption = minor;
		} else {
			age = VersionAge.NEWER_PATCH;
			bestOption = patch;
		}

		Icon icon = MavenUpdater.ICON;
		String upgradeTier = age.name().replace("NEWER_", "").toLowerCase();
		String upgradeTierDisplay = Character.toUpperCase(upgradeTier.charAt(0)) + upgradeTier.substring(1);
		String tooltip = MessageBundle.message("gutter.newer.tooltip", upgradeTierDisplay, bestOption.version().toString());
		String accessibleName = MessageBundle.message("gutter.newer.accessible");

		return new LineMarkerInfo<>(element,
				new TextRange(element.getTextRange().getStartOffset(), element.getTextRange().getStartOffset()), icon,
				e -> tooltip, (mouseEvent, psiElement) -> {
					AnAction action = ActionManager.getInstance().getAction("biz.paluch.dap.UpdateMavenDependencies");
					if (action != null) {
						ActionManager.getInstance().tryToExecute(action, mouseEvent, null, null, true);
					}
				}, GutterIconRenderer.Alignment.LEFT, () -> accessibleName);
	}
}
