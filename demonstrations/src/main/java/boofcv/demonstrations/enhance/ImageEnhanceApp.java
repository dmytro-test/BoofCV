/*
 * Copyright (c) 2022, Peter Abeles. All Rights Reserved.
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

package boofcv.demonstrations.enhance;

import boofcv.alg.enhance.EnhanceImageOps;
import boofcv.alg.enhance.GEnhanceImageOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.core.image.ConvertImage;
import boofcv.demonstrations.shapes.DetectBlackShapePanel;
import boofcv.gui.BoofSwingUtil;
import boofcv.gui.DemonstrationBase;
import boofcv.gui.controls.JCheckBoxValue;
import boofcv.gui.controls.JSpinnerNumber;
import boofcv.gui.image.ImageZoomPanel;
import boofcv.io.PathLabel;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;
import org.ddogleg.struct.DogArray_I32;
import pabeles.concurrency.GrowArray;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import static boofcv.gui.BoofSwingUtil.MAX_ZOOM;
import static boofcv.gui.BoofSwingUtil.MIN_ZOOM;

/**
 * Displays various image enhancement filters
 *
 * @author Peter Abeles
 */
public class ImageEnhanceApp extends DemonstrationBase {
	public static String HISTOGRAM_GLOBAL = "Histogram Global";
	public static String HISTOGRAM_LOCAL = "Histogram Local";
	public static String SHARPEN_4 = "Sharpen-4";
	public static String SHARPEN_8 = "Sharpen-8";

	ImageZoomPanel imagePanel = new ImageZoomPanel();
	ControlPanel controls = new ControlPanel();

	// storage for histogram
	int[] histogram = new int[256];
	int[] transform = new int[256];
	GrowArray<DogArray_I32> workArrays = new GrowArray<>(DogArray_I32::new);

	GrayU8 gray = new GrayU8(1, 1);
	GrayU8 enhancedGray = new GrayU8(1, 1);
	Planar<GrayU8> enhancedColor = new Planar<GrayU8>(GrayU8.class, 1, 1, 3);

	BufferedImage output = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

	public ImageEnhanceApp( List<?> exampleInputs ) {
		super(exampleInputs, ImageType.pl(3, GrayU8.class));

		imagePanel.setPreferredSize(new Dimension(800, 800));
		imagePanel.addMouseWheelListener(new MouseAdapter() {
			@Override
			public void mouseWheelMoved( MouseWheelEvent e ) {
				controls.setZoom(BoofSwingUtil.mouseWheelImageZoom(ImageEnhanceApp.this.controls.zoom, e));
			}
		});

		imagePanel.requestFocus();

		add(BorderLayout.WEST, controls);
		add(BorderLayout.CENTER, imagePanel);
	}

	@Override
	protected void handleInputChange( int source, InputMethod method, final int width, final int height ) {
		super.handleInputChange(source, method, width, height);

		gray.reshape(width, height);
		enhancedGray.reshape(width, height);
		enhancedColor.reshape(width, height);
		output = ConvertBufferedImage.checkDeclare(width, height, output, output.getType());

		BoofSwingUtil.invokeNowOrLater(() -> {
			double zoom = BoofSwingUtil.selectZoomToShowAll(imagePanel, width, height);
			controls.setImageSize(width, height);
			controls.setZoom(zoom);
			imagePanel.setScale(zoom);
			imagePanel.updateSize(width, height);
			imagePanel.getVerticalScrollBar().setValue(0);
			imagePanel.getHorizontalScrollBar().setValue(0);
		});
	}

	@Override
	public void processImage( int sourceID, long frameID, BufferedImage buffered, ImageBase _input ) {
		Planar<GrayU8> color = (Planar<GrayU8>)_input;

		ConvertImage.average(color, gray);

		if (controls.checkShowInput.value) {
			output.createGraphics().drawImage(buffered, 0, 0, null);
		} else {
			long before = System.nanoTime();
			if (controls.activeAlgorithm.equals(HISTOGRAM_GLOBAL)) {
				ImageStatistics.histogram(gray, 0, histogram);
				EnhanceImageOps.equalize(histogram, transform);
				if (controls.checkColor.value) {
					for (int i = 0; i < color.getNumBands(); i++) {
						EnhanceImageOps.applyTransform(color.getBand(i), transform, enhancedColor.getBand(i));
					}
				} else {
					EnhanceImageOps.applyTransform(gray, transform, enhancedGray);
				}
			} else if (controls.activeAlgorithm.equals(HISTOGRAM_LOCAL)) {
				if (controls.checkColor.value) {
					GEnhanceImageOps.equalizeLocal(color, controls.radius, enhancedColor, 256, workArrays);
				} else {
					EnhanceImageOps.equalizeLocal(gray, controls.radius, enhancedGray, 256, workArrays);
				}
			} else if (controls.activeAlgorithm.equals(SHARPEN_4)) {
				if (controls.checkColor.value) {
					GEnhanceImageOps.sharpen4(color, enhancedColor);
				} else {
					GEnhanceImageOps.sharpen4(gray, enhancedGray);
				}
			} else if (controls.activeAlgorithm.equals(SHARPEN_8)) {
				if (controls.checkColor.value) {
					GEnhanceImageOps.sharpen8(color, enhancedColor);
				} else {
					GEnhanceImageOps.sharpen8(gray, enhancedGray);
				}
			}
			long after = System.nanoTime();

			controls.setProcessingTimeS((after - before)*1e-9);

			if (controls.checkColor.value) {
				ConvertBufferedImage.convertTo(enhancedColor, output, true);
			} else {
				ConvertBufferedImage.convertTo(enhancedGray, output);
			}
		}

		SwingUtilities.invokeLater(() -> {
			imagePanel.setImage(output);
			imagePanel.repaint();
		});
	}

	protected void handleSettingsChanged() {
		if (inputMethod == InputMethod.IMAGE) {
			reprocessInput();
		}
	}

	@SuppressWarnings({"JdkObsolete", "NullAway.Init"})
	class ControlPanel extends DetectBlackShapePanel {
		Vector<String> comboBoxItems = new Vector<>();
		JComboBox<String> comboAlgorithms;
		List<Runnable> comboRunnable = new ArrayList<>();

		JSpinnerNumber spinnerRadius = spinnerWrap(50, 1, 200, 1);
		JCheckBoxValue checkShowInput = checkboxWrap("Show Input", false);
		JCheckBoxValue checkColor = checkboxWrap("Color", false);

		String activeAlgorithm;
		protected int radius = 50;

		public ControlPanel() {
			final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(comboBoxItems);
			comboAlgorithms = new JComboBox(model);
			comboAlgorithms.addActionListener(actionEvent -> {
				int selectedAlgorithm = comboAlgorithms.getSelectedIndex();
				activeAlgorithm = (String)comboAlgorithms.getModel().getSelectedItem();
				comboRunnable.get(selectedAlgorithm).run();
				handleSettingsChanged();
			});

			selectZoom = spinner(1.0, MIN_ZOOM, MAX_ZOOM, 1.0);

			addAlgorithms();
			comboAlgorithms.setSelectedIndex(0);
			comboAlgorithms.setMaximumSize(comboAlgorithms.getPreferredSize());

			addLabeled(processingTimeLabel, "Time (ms)");
			addLabeled(imageSizeLabel, "Size");
			add(comboAlgorithms);
			addAlignLeft(checkShowInput.check);
			addAlignLeft(checkColor.check);
			addLabeled(selectZoom, "Zoom");
			addLabeled(spinnerRadius.spinner, "Radius");
			addVerticalGlue(this);
		}

		private void addAlgorithms() {
			addAlgorithm(HISTOGRAM_GLOBAL, false);
			addAlgorithm(HISTOGRAM_LOCAL, true);
			addAlgorithm(SHARPEN_4, false);
			addAlgorithm(SHARPEN_8, false);
		}

		public void addAlgorithm( String name, final boolean usesRadius ) {
			comboBoxItems.add(name);
			comboRunnable.add(() -> spinnerRadius.spinner.setEnabled(usesRadius));
		}

		@Override public void controlChanged( final Object source ) {
			if (source == selectZoom) {
				// tell the image that it's zoom factor has changed
				imagePanel.setScale(controls.zoom);
				imagePanel.repaint();
				return;
			}
			handleSettingsChanged();
		}
	}

	public static void main( String[] args ) {
		var examples = new ArrayList<PathLabel>();
		examples.add(new PathLabel("dark", UtilIO.pathExample("enhance/dark.jpg")));
		examples.add(new PathLabel("dull", UtilIO.pathExample("enhance/dull.jpg")));
		examples.add(new PathLabel("dark qr", UtilIO.pathExample("fiducial/qrcode/image04.jpg")));
		examples.add(new PathLabel("rubix", UtilIO.pathExample("background/rubixfire.mp4")));
		examples.add(new PathLabel("intersection", UtilIO.pathExample("background/street_intersection.mp4")));
		examples.add(new PathLabel("night driving", UtilIO.pathExample("tracking/night_follow_car.mjpeg")));

		SwingUtilities.invokeLater(() -> {
			var app = new ImageEnhanceApp(examples);

			app.openExample(examples.get(0));
			app.display("Image Enhancement");
		});
	}
}
