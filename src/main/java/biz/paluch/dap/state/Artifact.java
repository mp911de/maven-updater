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
package biz.paluch.dap.state;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Release;

import java.util.ArrayList;
import java.util.List;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;

@Tag("artifact")
public class Artifact {

	private @Attribute String groupId;
	private @Attribute String artifactId;

	private final @XCollection(propertyElementName = "releases", elementName = "release",
			style = XCollection.Style.v2) List<biz.paluch.dap.state.Release> releases = new ArrayList<>();

	public Artifact() {}

	public Artifact(String groupId, String artifactId) {
		this.groupId = groupId;
		this.artifactId = artifactId;
	}

	public Artifact(ArtifactId artifactId) {
		this(artifactId.groupId(), artifactId.artifactId());
	}

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public List<biz.paluch.dap.state.Release> getReleases() {
		return releases;
	}

	public boolean matches(ArtifactId artifactId) {
		return getArtifactId().equals(artifactId.artifactId()) && getGroupId().equals(artifactId.groupId());
	}

	@Transient
	public List<Release> getVersionOptions() {

		List<Release> options = new ArrayList<>();
		for (biz.paluch.dap.state.Release release : releases) {
			options.add(release.toVersionOption());
		}
		return options;
	}

	public void setVersionOptions(List<Release> releases) {
		this.releases.clear();
		for (Release release : releases) {
			this.releases.add(biz.paluch.dap.state.Release.from(release));
		}
	}

	public ArtifactId toArtifactId() {
		return ArtifactId.of(getGroupId(), getArtifactId());
	}

}
