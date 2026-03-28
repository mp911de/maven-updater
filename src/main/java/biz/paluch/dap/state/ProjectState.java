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
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;

import org.jspecify.annotations.Nullable;

/**
 * Interface for project state.
 *
 * @author Mark Paluch
 */
public interface ProjectState {

	/**
	 * Find a dependency by its Maven coordinates.
	 */
	@Nullable
	Dependency findDependency(ArtifactId artifactId);

	/**
	 * Set the dependencies of the project.
	 */
	void setDependencies(DependencyCollector collector);

	/**
	 * Check whether the project has dependencies.
	 */
	boolean hasDependencies();

	/**
	 * Invalidate all dependencies.
	 */
	void invalidateDependencies();

	/**
	 * Get a property by its name.
	 */
	@Nullable
	Property getProperty(String propertyName);

}
