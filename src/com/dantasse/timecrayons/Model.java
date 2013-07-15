package com.dantasse.timecrayons;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeriesDataItem;

import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import au.com.bytecode.opencsv.CSVWriter;

public class Model {

    public void train(List<TimePeriod> selectedTimes, TimeSeriesCollection dataset) {
        // argh. weka really wants you to read in an arff file to get started.
        // okay then.
        writeCsvFile(selectedTimes, dataset, "foo.csv");
        Instances instances = null;
        try {
            instances = new DataSource("foo.csv").getDataSet();
        } catch (Exception e) {
            e.printStackTrace(); // welp
        }
        for (int i = 0; i < instances.numInstances(); i++) {
            System.out.println(instances.instance(i));
        } // TODO pick up here
    }

    /**
     * Writes features to a dummy CSV file so that you can read it back in with
     * weka.
     */
    private void writeCsvFile(List<TimePeriod> selectedTimes, TimeSeriesCollection dataset, String filename) {
        CSVWriter writer = null;
        try {
            writer = new CSVWriter(new FileWriter(filename));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        writer.writeNext(new String[] {"f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8"}); //TODO
        for(TimePeriod selectedTime : selectedTimes) {
            TimeSeriesCollection subset = makeSubset(dataset, selectedTime.getStart(), selectedTime.getEnd());
            List<Double> features = generateFeatures(subset);
            List<String> featuresStr = new ArrayList<String>();
            for (Double feature : features) {
                featuresStr.add(feature.toString());
            }
            writer.writeNext(featuresStr.toArray(new String[featuresStr.size()])); // java!
        }
        try {
            writer.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private TimeSeriesCollection makeSubset(TimeSeriesCollection dataset, Date start, Date end) {
        TimeSeriesCollection subset = new TimeSeriesCollection();
        for (Object tsObj : dataset.getSeries()) {
            TimeSeries ts = (TimeSeries) tsObj; // why couldn't getSeries return a List<TimeSeries>?!
            TimeSeries newTs = new TimeSeries("new time series");
            for (Object itemObj : ts.getItems()) {
                TimeSeriesDataItem item = (TimeSeriesDataItem) itemObj; //ugh ugh
                RegularTimePeriod period = item.getPeriod();
                if (period.getStart().after(start) && period.getEnd().before(end)) {
                    newTs.add(item);
                }
            }
            subset.addSeries(newTs);
        }
        return subset;
    }
    
    // work in progress, obviously, and very specific to this project
    private List<Double> generateFeatures(TimeSeriesCollection data) {
        List<Double> features = new ArrayList<Double>();
        
        // Features 0 to 8: min, max, mean 
        features.add(data.getSeries(0).getMinY());
        features.add(data.getSeries(0).getMaxY());
        features.add(mean(data.getSeries(0)));
        features.add(data.getSeries(1).getMinY());
        features.add(data.getSeries(1).getMaxY());
        features.add(mean(data.getSeries(1)));
        features.add(data.getSeries(2).getMinY());
        features.add(data.getSeries(2).getMaxY());
        features.add(mean(data.getSeries(2)));

        return features;
    }
    
    private double mean(TimeSeries ts) {
        double sum = 0;
        for (Object itemObj : ts.getItems()) {
            TimeSeriesDataItem item = (TimeSeriesDataItem) itemObj;
            sum += item.getValue().doubleValue();
        }
        return sum / ts.getItemCount();
    }
}
