/*
 * Copyright (c) 2011-2019, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.feature.disparity.block.score;

import boofcv.alg.feature.disparity.block.DisparitySparseScoreSadRect;
import boofcv.struct.border.ImageBorder_F32;
import boofcv.struct.image.GrayF32;

import java.util.Arrays;

/**
 * <p>
 * Implementation of {@link DisparitySparseScoreSadRect} that processes images of type {@link GrayF32}.
 * </p>
 *
 * <p>
 * DO NOT MODIFY. Generated by {@link GenerateDisparitySparseScoreSadRect}.
 * </p>
 *
 * @author Peter Abeles
 */
public class DisparitySparseScoreBM_SAD_F32 extends DisparitySparseScoreSadRect<float[],GrayF32> {

	// scores up to the maximum baseline
	float[] scores;

	public DisparitySparseScoreBM_SAD_F32(int minDisparity , int maxDisparity, int radiusX, int radiusY) {
		super(minDisparity,maxDisparity,radiusX, radiusY);

		scores = new float[ rangeDisparity ];
	}

	@Override
	public boolean process( int x , int y ) {
		// can't estimate disparity if there are no pixels it can estimate disparity from
		if( x < minDisparity )
			return false;

		// adjust disparity for image border
		localMaxRange = Math.min(x,maxDisparity)-minDisparity+1;

		Arrays.fill(scores,0);
		if( x < localMaxRange+radiusX+minDisparity || x >= left.width-radiusX || y < radiusY || y >= left.height-radiusY )
			scoreBorder(x,y);
		else
			scoreInner(x, y);

		return true;
	}

	private void scoreBorder(int cx, int cy) {
		ImageBorder_F32 bleft = (ImageBorder_F32)this.bleft;
		ImageBorder_F32 bright = (ImageBorder_F32)this.bright;

		// sum up horizontal errors in the region
		for (int y = -radiusY; y <= radiusY; y++) {
			for (int d = 0; d < localMaxRange; d++) {
				float score = 0;
				for (int x = -radiusX; x <= radiusX; x++) {
					float diff = bleft.get(cx+x,cy+y)-bright.get(cx+x-d-minDisparity,+cy+y);
					score += Math.abs(diff);
				}
				scores[d] += score;
			}
		}
	}

	private void scoreInner(int x, int y) {
		// sum up horizontal errors in the region
		for( int row = 0; row < regionHeight; row++ ) {
			// pixel indexes
			int startLeft = left.startIndex + left.stride*(y-radiusY+row) + x-radiusX;
			int startRight = right.startIndex + right.stride*(y-radiusY+row) + x-radiusX-minDisparity;

			for(int i = 0; i < localMaxRange; i++ ) {
				int indexLeft = startLeft;
				int indexRight = startRight-i;

				float score = 0;
				for( int j = 0; j < regionWidth; j++ ) {
					float diff = (left.data[ indexLeft++ ]) - (right.data[ indexRight++ ]);

					score += Math.abs(diff);
				}
				scores[i] += score;
			}
		}
	}

	@Override
	public float[] getScore() {
		return scores;
	}

	@Override
	public Class<GrayF32> getImageType() {
		return GrayF32.class;
	}

}
