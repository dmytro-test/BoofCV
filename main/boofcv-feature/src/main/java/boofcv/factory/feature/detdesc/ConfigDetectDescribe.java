/*
 * Copyright (c) 2011-2020, Peter Abeles. All Rights Reserved.
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

package boofcv.factory.feature.detdesc;

import boofcv.abst.feature.describe.*;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.detect.interest.ConfigPointDetector;
import boofcv.abst.feature.detect.interest.ConfigSiftDetector;
import boofcv.abst.feature.orientation.ConfigOrientation2;
import boofcv.factory.feature.describe.ConfigDescribeRegionPoint;
import boofcv.factory.feature.detect.interest.ConfigDetectInterestPoint;
import boofcv.struct.Configuration;

/**
 * @author Peter Abeles
 */
public class ConfigDetectDescribe implements Configuration {
	/** The feature descriptor is used. Not always used. */
	public ConfigDescribeRegionPoint.DescriptorType typeDescribe = ConfigDescribeRegionPoint.DescriptorType.SURF_FAST;
	/** The feature detector is used. Not always used. */
	public ConfigDetectInterestPoint.DetectorType typeDetector = ConfigDetectInterestPoint.DetectorType.FAST_HESSIAN;

	/** Describes the scale-space used by SIFT detector / descriptor. */
	public ConfigSiftScaleSpace scaleSpaceSift = new ConfigSiftScaleSpace();

	public ConfigSurfDescribe.Fast describeSurfFast = new ConfigSurfDescribe.Fast();
	public ConfigSurfDescribe.Stability describeSurfStability = new ConfigSurfDescribe.Stability();
	public ConfigSiftDescribe describeSift = new ConfigSiftDescribe();
	public ConfigBrief describeBrief = new ConfigBrief(false);
	public ConfigTemplateDescribe describeTemplate = new ConfigTemplateDescribe();

	/** Configuration for point based detectors (e.g. corners and blob) */
	public ConfigPointDetector detectPoint = new ConfigPointDetector();
	/** Fast Hessian scale invariant blob detector. This is what SURF uses */
	public ConfigFastHessian detectFastHessian = new ConfigFastHessian();
	/** SIFT scale invariant blob detector */
	public ConfigSiftDetector detectSift = new ConfigSiftDetector();

	/** Configuration for estimating the region's orientation */
	public ConfigOrientation2 orientation = new ConfigOrientation2();

	public void copyRefTo(ConfigDescribeRegionPoint dst ) {
		dst.type = this.typeDescribe;
		dst.scaleSpaceSift = this.scaleSpaceSift;
		dst.template = this.describeTemplate;
		dst.surfFast = this.describeSurfFast;
		dst.surfStability = this.describeSurfStability;
		dst.brief = this.describeBrief;
		dst.sift = this.describeSift;
	}

	public void copyRefFrom(ConfigDescribeRegionPoint src ) {
		this.typeDescribe = src.type;
		this.scaleSpaceSift = src.scaleSpaceSift;
		this.describeTemplate = src.template;
		this.describeSurfFast = src.surfFast;
		this.describeSurfStability = src.surfStability;
		this.describeBrief = src.brief;
		this.describeSift = src.sift;
	}

	@Override
	public void checkValidity() {
		scaleSpaceSift.checkValidity();

		describeSurfFast.checkValidity();
		describeSurfStability.checkValidity();
		describeSift.checkValidity();
		describeBrief.checkValidity();
		describeTemplate.checkValidity();

		detectPoint.checkValidity();
		detectFastHessian.checkValidity();
		detectSift.checkValidity();
	}

	public void setTo( ConfigDetectDescribe src ) {
		this.typeDescribe = src.typeDescribe;
		this.typeDetector = src.typeDetector;
		this.scaleSpaceSift.setTo(src.scaleSpaceSift);
		this.describeSurfFast.setTo(src.describeSurfFast);
		this.describeSurfStability.setTo(src.describeSurfStability);
		this.describeSift.setTo(src.describeSift);
		this.describeBrief.setTo(src.describeBrief);
		this.describeTemplate.setTo(src.describeTemplate);
		this.detectPoint.setTo(src.detectPoint);
		this.detectFastHessian.setTo(src.detectFastHessian);
		this.detectSift.setTo(src.detectSift);
	}

	public ConfigDetectDescribe copy() {
		var out = new ConfigDetectDescribe();
		out.setTo(this);
		return out;
	}
}
