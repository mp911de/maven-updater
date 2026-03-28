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

import java.awt.image.RGBImageFilter;

/**
 * {@link RGBImageFilter} that scales the alpha channel of every pixel by a fixed factor, making the image uniformly
 * (semi-)transparent while preserving its RGB colours.
 * <p>
 * Usage with AWT:
 *
 * <pre>{@code
 * AlphaImageFilter filter = new AlphaImageFilter(0.5f);   // 50% opacity
 * Image src = ...;
 * Image result = Toolkit.getDefaultToolkit()
 *         .createImage(new FilteredImageSource(src.getSource(), filter));
 * }</pre>
 */
public class AlphaImageFilter extends RGBImageFilter {

	private final float alphaFactor;

	/**
	 * @param alphaFactor opacity to apply, in the range {@code [0.0, 1.0]}. {@code 0.0} produces a fully transparent
	 *          image; {@code 1.0} leaves the image unchanged.
	 * @throws IllegalArgumentException if {@code alphaFactor} is outside {@code [0.0, 1.0]}
	 */
	public AlphaImageFilter(float alphaFactor) {
		if (alphaFactor < 0f || alphaFactor > 1f) {
			throw new IllegalArgumentException("alphaFactor must be in [0.0, 1.0], was: " + alphaFactor);
		}
		this.alphaFactor = alphaFactor;
		// Safe to filter IndexColorModels in-place: the filter rewrites every pixel individually.
		canFilterIndexColorModel = true;
	}

	/**
	 * Scales the alpha component of the given ARGB pixel by {@link #alphaFactor}, leaving the RGB channels unchanged.
	 */
	@Override
	public int filterRGB(int x, int y, int argb) {
		int originalAlpha = (argb >>> 24) & 0xFF;
		int scaledAlpha = Math.round(originalAlpha * alphaFactor);
		return (scaledAlpha << 24) | (argb & 0x00FFFFFF);
	}
}
