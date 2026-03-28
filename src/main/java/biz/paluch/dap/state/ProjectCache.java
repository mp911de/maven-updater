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

import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.VersionSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jetbrains.idea.maven.model.MavenId;
import org.jspecify.annotations.Nullable;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;

/**
 * @author Mark Paluch
 */
@Tag("project")
public class ProjectCache implements Comparator<ProjectCache> {

	static final Comparator<ProjectCache> COMPARATOR = Comparator.comparing(ProjectCache::getGroupId)
			.thenComparing(ProjectCache::getArtifactId);

	private @Attribute String artifactId;
	private @Attribute String groupId;

	private final @Tag @XCollection(propertyElementName = "properties", elementName = "property",
			style = XCollection.Style.v2) List<Property> properties = new ArrayList<>();

	@Transient private final Map<String, Property> propertyMap = new java.util.TreeMap<>();

	public ProjectCache() {}

	public ProjectCache(MavenId mavenId) {
		this.artifactId = mavenId.getArtifactId();
		this.groupId = mavenId.getGroupId();
	}

	public String getArtifactId() {
		return artifactId;
	}

	public void setArtifactId(String artifactId) {
		this.artifactId = artifactId;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	/**
	 * Returns all known property-to-artifact mappings. Each {@link Property} carries the property name and the
	 * artifact(s) whose version it controls.
	 */
	public List<Property> getProperties() {
		return List.copyOf(properties);
	}

	/**
	 * Update the cache with the given properties.
	 */
	@Transient
	public void setProperties(Collection<Dependency> dependencies) {

		properties.clear();
		propertyMap.clear();

		for (Dependency candidate : dependencies) {

			for (VersionSource versionSource : candidate.getVersionSources()) {
				if (versionSource instanceof VersionSource.VersionPropertySource vps) {

					Property property = propertyMap.computeIfAbsent(vps.getProperty(), Property::new);
					property.addArtifact(candidate.getArtifactId());
				}
			}
		}

		properties.addAll(propertyMap.values());
	}

	@Transient
	public @Nullable Property getProperty(String propertyName) {

		if (propertyMap.size() != properties.size()) {

			propertyMap.clear();
			for (Property property : properties) {
				propertyMap.put(property.name(), property);
			}
		}

		return propertyMap.get(propertyName);
	}

	public boolean matches(MavenId mavenId) {
		return this.groupId.equals(mavenId.getGroupId()) && this.artifactId.equals(mavenId.getArtifactId());
	}

	@Override
	public int compare(ProjectCache o1, ProjectCache o2) {
		return COMPARATOR.compare(o1, o2);
	}

}
