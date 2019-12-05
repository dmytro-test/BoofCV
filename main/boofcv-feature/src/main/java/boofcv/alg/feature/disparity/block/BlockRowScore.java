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

package boofcv.alg.feature.disparity.block;

import boofcv.struct.border.ImageBorder;
import boofcv.struct.border.ImageBorder_F32;
import boofcv.struct.border.ImageBorder_S32;
import boofcv.struct.border.ImageBorder_S64;
import boofcv.struct.image.*;

/**
 * Interface for computing disparity scores across an entire row
 *
 * <p>MUST BE THREAD SAFE</p>
 *
 * @author Peter Abeles
 */
public interface BlockRowScore<T extends ImageBase<T>,Array> {

	void setBorder( ImageBorder<T> border );

	/**
	 * Specifies the input images
	 *
	 * @param left left image
	 * @param right right image
	 */
	void setInput( T left, T right );

	/**
	 * For a given disparity, the score for each region on the left share many components in common.
	 * Because of this the scores are computed with disparity being the outer most loop
	 *
	 * @param row Image row being examined
	 * @param scores Storage for disparity scores.
	 * @param minDisparity Minimum disparity to consider
	 * @param maxDisparity Maximum disparity to consider
	 * @param regionWidth Size of the sample region's width
	 * @param elementScore Storage for scores of individual pixels
	 */
	void scoreRow(int row, Array scores,
				  int minDisparity , int maxDisparity , int regionWidth ,
				  Array elementScore);

	void score(int indexLeft, int indexRight, int offset, int length, Array elementScore);

	void scoreBorder( int x0 , int y0, int d , int offset, int length , Array elementScore );

	/**
	 * Returns the maximum error each pixel in the region can contribute
	 * {@link RuntimeException} should be thrown.
	 * @return Largest possible error for the region.
	 */
	int getMaxPerPixelError();

	/**
	 * If true then the score needs to be normalized
	 */
	boolean isRequireNormalize();

	/**
	 * Additional normalization that's applied after the score for a region is known. Currently only used by
	 * {@link BlockRowScoreNcc}.
	 *
	 * @param row Image row being examined
	 * @param scores Storage for disparity scores.
	 * @param minDisparity Minimum disparity to consider
	 * @param maxDisparity Maximum disparity to consider
	 * @param regionWidth Size of the sample region's width
	 * @param regionHeight Size of the sample region's height
	 */
	void normalizeRegionScores(int row, Array scores,
							   int minDisparity, int maxDisparity, int regionWidth, int regionHeight,
							   Array scoresNorm );

	/**
	 * Applies normalization to a single row
	 *
	 * @param row Row that is being normalized
	 * @param colLeft column in left image
	 * @param colRight column in right image
	 * @param numCols number of columns
	 * @param regionWidth width of the region
	 * @param regionHeight height of the region
	 * @param scores array with scores that are to be normalized
	 * @param indexScores first index in scores that is to be normalized
	 */
	void normalizeScore( int row , int colLeft , int colRight , int numCols, int regionWidth , int regionHeight,
						 Array scores , int indexScores , Array scoresNorm );

	ImageType<T> getImageType();

	abstract class ArrayS32<T extends ImageBase<T>> implements BlockRowScore<T,int[]> {
		protected int maxPerPixel;
		T left, right;

		protected ArrayS32( int maxPerPixel ) {
			this.maxPerPixel = maxPerPixel;
		}

		@Override
		public void setInput(T left, T right) {
			this.left = left;
			this.right = right;
		}

		@Override
		public void scoreRow(int row, int[] scores,
							 int minDisparity, int maxDisparity, int regionWidth,
							 int[] elementScore) {
			int regionRadius = regionWidth/2;
			int widthAndBorder = left.width+regionRadius*2; // image width plus left and right borders

			// disparity as the outer loop to maximize common elements in inner loops, reducing redundant calculations
			for( int d = minDisparity; d <= maxDisparity; d++ ) {
				int dispFromMin = d - minDisparity;

				// TODO Alternative approach. Create a with border pixels instead?
				//      Simpler but not sure if it's faster or slower.
				// Fill elementScore with scores for individual elements for this row at disparity d
				if( row >= 0 && row < left.height ) {
					// Compute in segments because using the border function is expensive while score() can
					// be computed very fast
					int x0 = dispFromMin-regionRadius+minDisparity;
					int x1 = dispFromMin+minDisparity;
					int x2 = left.width;
					int x3 = x2+regionRadius;
					int offset = 0;

					// border at lower extent. Left image pixels 0 to r-1
					scoreBorder(x0,row,d,offset,x1-x0,elementScore);
					offset += x1-x0;

					// indexes that data is read to/from for different data structures
					int indexLeft = left.startIndex + left.stride * row + d;
					int indexRight = right.startIndex + right.stride * row;
					score(indexLeft, indexRight, offset,x2-x1, elementScore);
					offset += x2-x1;

					// border at lower extent. Left image pixels colMax-r to colMax-1
					scoreBorder(x2,row,d,offset,x3-x2,elementScore);
				} else {
					scoreBorder(dispFromMin-regionRadius+minDisparity,row,d,0,widthAndBorder-dispFromMin-minDisparity,elementScore);
				}

				// score at the first column
				int score = 0;
				for( int i = 0; i < regionWidth; i++ )
					score += elementScore[i];

				int indexScore = left.width * dispFromMin + dispFromMin;
				scores[indexScore++] = score;

				// scores for the remaining columns
				for( int col = 0; col < left.width-dispFromMin-1; col++ , indexScore++ ) {
					scores[indexScore] = score += elementScore[col+regionWidth] - elementScore[col];
				}
			}
		}

		@Override
		public void normalizeRegionScores(int row, int[] scores,
										  int minDisparity, int maxDisparity, int regionWidth, int regionHeight,
										  int[] scoresNorm )
		{
			// disparity as the outer loop to maximize common elements in inner loops, reducing redundant calculations
			for( int d = minDisparity; d <= maxDisparity; d++ ) {
				int dispFromMin = d - minDisparity;

				// number of individual columns the error is computed in
				final int colMax = left.width-d;

				// indexes that data is read to/from for different data structures
				int indexScore = left.width*dispFromMin + dispFromMin;

				normalizeScore(row,d,0,colMax,regionWidth,regionHeight,scores,indexScore,scoresNorm);
			}
		}

		@Override
		public void normalizeScore(int row, int colLeft, int colRight, int numCols, int regionWidth, int regionHeight,
								   int[] scores, int indexScores, int[] scoresNorm) {}

		@Override
		public int getMaxPerPixelError() {
			if( maxPerPixel < 0 )
				throw new RuntimeException("Not supported");
			return maxPerPixel;
		}
	}

	abstract class ArrayS32_BS32<T extends GrayI<T>> extends ArrayS32<T> {
		ImageBorder_S32<T> borderLeft;
		ImageBorder_S32<T> borderRight;

		protected ArrayS32_BS32(int maxPerPixel) {
			super(maxPerPixel);
		}

		@Override
		public void setBorder(ImageBorder<T> border) {
			this.borderLeft = (ImageBorder_S32<T>)border.copy();
			this.borderRight = (ImageBorder_S32<T>)border.copy();
		}

		@Override
		public void setInput(T left, T right) {
			super.setInput(left, right);
			borderLeft.setImage(left);
			borderRight.setImage(right);
		}
	}

	abstract class ArrayS32_BS64 extends ArrayS32<GrayS64> {
		ImageBorder_S64 borderLeft;
		ImageBorder_S64 borderRight;

		protected ArrayS32_BS64(int maxPerPixel) {
			super(maxPerPixel);
		}

		@Override
		public void setBorder(ImageBorder<GrayS64> border) {
			this.borderLeft = (ImageBorder_S64)border.copy();
			this.borderRight = (ImageBorder_S64)border.copy();
		}

		@Override
		public void setInput(GrayS64 left, GrayS64 right) {
			super.setInput(left, right);
			borderLeft.setImage(left);
			borderRight.setImage(right);
		}
	}

	abstract class ArrayF32<T extends ImageBase<T>> implements BlockRowScore<T,float[]> {
		T left, right;

		@Override
		public void setInput(T left, T right) {
			this.left = left;
			this.right = right;
		}

		@Override
		public void scoreRow(int row, float[] scores,
							 int minDisparity, int maxDisparity, int regionWidth,
							 float[] elementScore) {
			int regionRadius = regionWidth/2;
			int widthAndBorder = left.width+2*regionRadius;

			// disparity as the outer loop to maximize common elements in inner loops, reducing redundant calculations
			for( int d = minDisparity; d <= maxDisparity; d++ ) {
				int dispFromMin = d - minDisparity;

				// Fill elementScore with scores for individual elements for this row at disparity d
				if( row >= 0 && row < left.height ) {
					// Compute in segments because using the border function is expensive while score() can
					// be computed very fast
					int x0 = dispFromMin-regionRadius+minDisparity;
					int x1 = dispFromMin+minDisparity;
					int x2 = left.width;
					int x3 = x2+regionRadius;
					int offset = 0;

					// border at lower extent. Left image pixels 0 to r-1
					scoreBorder(x0,row,d,offset,x1-x0,elementScore);
					offset += x1-x0;

					// indexes that data is read to/from for different data structures
					int indexLeft = left.startIndex + left.stride * row + d;
					int indexRight = right.startIndex + right.stride * row;
					score(indexLeft, indexRight, offset,x2-x1, elementScore);
					offset += x2-x1;

					// border at lower extent. Left image pixels colMax-r to colMax-1
					scoreBorder(x2,row,d,offset,x3-x2,elementScore);
				} else {
					scoreBorder(dispFromMin-regionRadius+minDisparity,row,d,0,widthAndBorder-dispFromMin-minDisparity,elementScore);
				}

				// score at the first column
				float score = 0;
				for( int i = 0; i < regionWidth; i++ )
					score += elementScore[i];

				int indexScore = left.width * dispFromMin + dispFromMin;
				scores[indexScore++] = score;

				// scores for the remaining columns
				for( int col = 0; col < left.width-dispFromMin-1; col++ , indexScore++ ) {
					scores[indexScore] = score += elementScore[col+regionWidth] - elementScore[col];
				}
			}
		}

		@Override
		public void normalizeRegionScores(int row, float[] scores,
										  int minDisparity, int maxDisparity, int regionWidth, int regionHeight, float[] scoresNorm ) {
			// disparity as the outer loop to maximize common elements in inner loops, reducing redundant calculations
			for( int d = minDisparity; d <= maxDisparity; d++ ) {
				int dispFromMin = d - minDisparity;

				// number of individual columns the error is computed in
				final int colMax = left.width-d;

				// indexes that data is read to/from for different data structures
				int indexScore = left.width*dispFromMin + dispFromMin;

				normalizeScore(row,d,0,colMax,regionWidth,regionHeight,scores,indexScore,scoresNorm);
			}
		}

		@Override
		public void normalizeScore(int row, int colLeft, int colRight, int numCols, int regionWidth, int regionHeight,
								   float[] scores, int indexScores, float[] scoresNorm) {}

		@Override
		public int getMaxPerPixelError() {
			throw new RuntimeException("Maximum error is not supported for the image type");
		}

	}

	abstract class ArrayS32_BF32 extends ArrayF32<GrayF32> {
		ImageBorder_F32 borderLeft;
		ImageBorder_F32 borderRight;

		@Override
		public void setBorder(ImageBorder<GrayF32> border) {
			this.borderLeft = (ImageBorder_F32)border.copy();
			this.borderRight = (ImageBorder_F32)border.copy();
		}

		@Override
		public void setInput(GrayF32 left, GrayF32 right) {
			super.setInput(left, right);
			borderLeft.setImage(left);
			borderRight.setImage(right);
		}
	}
}
