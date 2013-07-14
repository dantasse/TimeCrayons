package com.dantasse.timecrayons;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYBoxAnnotation;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.io.CSV;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.SimpleTimePeriod;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeriesDataItem;
import org.jfree.data.xy.XYDataset;

public class LineChart extends JFrame {
    private static final long serialVersionUID = 1L;

    private List<TimePeriod> selectedTimes = new ArrayList<TimePeriod>();
    private List<XYBoxAnnotation> annotationBoxes = new ArrayList<XYBoxAnnotation>();
    private double x1 = 0; // x coordinate where you started dragging
    private double tempBoxX1 = 0;
    private XYBoxAnnotation tempBox = new XYBoxAnnotation(0, 0, 0, 0);
    private JButton button = new JButton("Train Model");
    private Model model;

    public LineChart(String applicationTitle, String chartTitle) {
        super(applicationTitle);
        XYDataset dataset = createDataset();
        final JFreeChart chart = createChart(dataset, chartTitle);
        final ChartPanel chartPanel = new ChartPanel(chart);
        setContentPane(chartPanel);
        chartPanel.setPreferredSize(new java.awt.Dimension(1000, 600));
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
        chartPanel.add(button);

        chartPanel.addMouseMotionListener(new MouseMotionAdapter() {

            @Override
            public void mouseDragged(MouseEvent e) {
                super.mouseDragged(e);
                double tempBoxX2 = chart
                        .getXYPlot()
                        .getDomainAxis()
                        .java2DToValue(e.getX(), chartPanel.getScreenDataArea(),
                                chart.getXYPlot().getDomainAxisEdge());
                chart.getXYPlot().removeAnnotation(tempBox);
                tempBox = new XYBoxAnnotation(Math.min(tempBoxX1, tempBoxX2), chart.getXYPlot()
                        .getRangeAxis().getLowerBound(), Math.max(tempBoxX1, tempBoxX2), chart
                        .getXYPlot().getRangeAxis().getUpperBound(), null, // line
                        null, // outline paint
                        new Color(0, 0, 128, 32)); // fill paint
                tempBox.setToolTipText("this is temporary");
                // ^ dumb hack b/c otherwise permanent boxes get removed when
                // removing this
                // temporary box as well.
                chart.getXYPlot().addAnnotation(tempBox);
            }
        });

        chartPanel.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseReleased(MouseEvent arg0) {
                chart.getXYPlot().removeAnnotation(tempBox);
                double x2 = chart
                        .getXYPlot()
                        .getDomainAxis()
                        .java2DToValue(arg0.getX(), chartPanel.getScreenDataArea(),
                                chart.getXYPlot().getDomainAxisEdge());
                selectedTimes.add(new SimpleTimePeriod((long) Math.min(x1, x2), (long) Math.max(x1,
                        x2)));
                XYBoxAnnotation annotationBox = new XYBoxAnnotation(Math.min(x1, x2), chart
                        .getXYPlot().getRangeAxis().getLowerBound(), Math.max(x1, x2), chart
                        .getXYPlot().getRangeAxis().getUpperBound(), null, // line
                        null, // outline paint
                        new Color(0, 0, 128, 32)); // fill paint
                annotationBoxes.add(annotationBox);
                chart.getXYPlot().addAnnotation(annotationBox);
            }

            @Override
            public void mousePressed(MouseEvent arg0) {
                x1 = chart
                        .getXYPlot()
                        .getDomainAxis()
                        .java2DToValue(arg0.getX(), chartPanel.getScreenDataArea(),
                                chart.getXYPlot().getDomainAxisEdge());
                tempBoxX1 = x1;
            }
        });
        
        model = new Model();
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                model.train(selectedTimes);
            }
        });
    }

    private XYDataset createDataset() {
        CSV csv = new CSV();
        CategoryDataset cat = null;
        try {
            cat = csv.readCategoryDataset(new FileReader("data/shortaccel.csv"));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        TimeSeries xValues = new TimeSeries("x acceleration");
        TimeSeries yValues = new TimeSeries("y acceleration");
        TimeSeries zValues = new TimeSeries("z acceleration");

        for (Object rowKey : cat.getRowKeys()) {
            long ms = cat.getValue((String) rowKey, "timestamp").longValue();
            double x = cat.getValue((String) rowKey, "double_values_0").doubleValue();
            double y = cat.getValue((String) rowKey, "double_values_1").doubleValue();
            double z = cat.getValue((String) rowKey, "double_values_2").doubleValue();
            xValues.addOrUpdate(new TimeSeriesDataItem(new Millisecond(new Date(ms)), x));
            yValues.addOrUpdate(new TimeSeriesDataItem(new Millisecond(new Date(ms)), y));
            zValues.addOrUpdate(new TimeSeriesDataItem(new Millisecond(new Date(ms)), z));
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(xValues);
        dataset.addSeries(yValues);
        dataset.addSeries(zValues);

        return dataset;
    }

    private JFreeChart createChart(XYDataset dataset, String title) {
        JFreeChart lineChart = ChartFactory.createTimeSeriesChart("accelerometer", // title
                "time", // category axis label
                "acceleration", // value axis label
                dataset, true, // legend
                true, // tooltips
                true); // urls
        return lineChart;
    }
}
