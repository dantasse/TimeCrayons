package com.dantasse.timecrayons;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.SimpleTimePeriod;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeriesDataItem;

import weka.classifiers.trees.J48;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import au.com.bytecode.opencsv.CSVWriter;

public class Model {

    private Random random = new Random();

    public void train(List<TimePeriod> selectedTimes, TimeSeriesCollection dataset) {
        // argh. weka really wants you to read in an arff file to get started.
        // okay then.
        writeCsvFile(selectedTimes, dataset, "foo.csv");
        Instances instances = null;
        try {
            instances = new DataSource("foo.csv").getDataSet();
            instances.setClassIndex(0);
        } catch (Exception e) {
            e.printStackTrace(); // welp
        }
        J48 decisionTree = new J48();
        
        try {
            decisionTree.buildClassifier(instances);
            decisionTree.classifyInstance(instances.instance(0));
        } catch (Exception e) {
            e.printStackTrace();
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
        writer.writeNext(new String[] {"class", "f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8"}); //TODO
        
        // Generate the Yes instances
        for(TimePeriod selectedTime : selectedTimes) {
            TimeSeriesCollection subset = makeSubset(dataset, selectedTime.getStart(), selectedTime.getEnd());
            List<Object> features = generateFeatures(subset, "yes");
            List<String> featuresStr = new ArrayList<String>();
            for (Object feature : features) {
                featuresStr.add(feature.toString());
            }
            writer.writeNext(featuresStr.toArray(new String[featuresStr.size()]));
        }
        
        // Generate the No instances
        // first, figure out how long/short instances can be (based on selected instances)
        // then, for each starting time, randomly select an interval starting then
        // throw out that interval if it overlaps a SelectedTime
        // then pick N intervals from that bag and use them as the "no" examples
        long shortestInterval = Long.MAX_VALUE;
        long longestInterval = 0;
        for (TimePeriod selectedTime : selectedTimes) {
            long length = selectedTime.getEnd().getTime() - selectedTime.getStart().getTime();
            if (length < shortestInterval)
                shortestInterval = length;
            if (length > longestInterval) 
                longestInterval = length;
        }
        
        List<TimePeriod> unselectedTimes = new ArrayList<TimePeriod>();
        // TODO: this only checks the first time series in the dataset
        for(Object timePeriodObj : dataset.getSeries(0).getTimePeriods()) {
            TimePeriod dataPoint = (TimePeriod) timePeriodObj;
            Date potentialStart = dataPoint.getStart();
            long potentialLength = (long) random.nextInt((int)(longestInterval - shortestInterval)) + shortestInterval;
            Date potentialEnd = new Date(potentialStart.getTime() + potentialLength);
            TimePeriod potentialTimePeriod = new SimpleTimePeriod(potentialStart, potentialEnd);
            if (!overlapsAny(potentialTimePeriod, selectedTimes)) {
                unselectedTimes.add(potentialTimePeriod);
            }
        }
        Collections.shuffle(unselectedTimes);
        unselectedTimes = unselectedTimes.subList(0, selectedTimes.size()); // TODO: only gets a few NO instances
        
        for (TimePeriod unselectedTime : unselectedTimes) {
            TimeSeriesCollection subset = makeSubset(dataset, unselectedTime.getStart(), unselectedTime.getEnd());
            List<Object> features = generateFeatures(subset, "no");
            List<String> featuresStr = new ArrayList<String>();
            for (Object feature : features) {
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

    /** @return true if the potentialTP overlaps any of the timePeriods, false if not */
    private boolean overlapsAny(TimePeriod potentialTP, List<TimePeriod> timePeriods) {
        for (TimePeriod tp : timePeriods) {
            if (tp.getStart().before(potentialTP.getEnd()) && tp.getEnd().after(potentialTP.getStart())) {
                return true;
            }
        }
        return false;
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
    private List<Object> generateFeatures(TimeSeriesCollection data, String classValue) {
        List<Object> features = new ArrayList<Object>();
        
        features.add(classValue);
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
