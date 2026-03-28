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

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;

@Tag("release")
public class Release {

	@Attribute private String version;
	@Attribute private @Nullable String date;

	public Release() {}

	public Release(String version, @Nullable String date) {
		this.version = version;
		this.date = date;
	}

	public static Release from(biz.paluch.dap.artifact.Release release) {
		if (release.releaseDate() != null) {
			return new Release(release.version().toString(), release.releaseDate().toLocalDate().toString());
		}
		return new Release(release.version().toString(), null);
	}

	@Transient
	public biz.paluch.dap.artifact.Release toVersionOption() {
		return biz.paluch.dap.artifact.Release.from(version(), date());
	}

	@Attribute
	public String version() {
		return version;
	}

	@Attribute
	public @Nullable String date() {
		return date;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		var that = (Release) obj;
		return Objects.equals(this.version, that.version) && Objects.equals(this.date, that.date);
	}

	@Override
	public int hashCode() {
		return Objects.hash(version, date);
	}

	@Override
	public String toString() {
		return "Release[" + "version=" + version + ", " + "date=" + date + ']';
	}

}
