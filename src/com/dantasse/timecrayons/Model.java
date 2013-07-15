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
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import au.com.bytecode.opencsv.CSVWriter;

public class Model {

    private Random random = new Random();
    /** |instances| is the feature vectors. */
    private Instances instances = null;
    private J48 decisionTree;

    public void train(List<TimePeriod> selectedTimes, TimeSeriesCollection timeSerieses) {
        // argh. weka really wants you to read in an arff file to get started. okay then.
        writeCsvFile(selectedTimes, timeSerieses, "foo.csv");
        try {
            instances = new DataSource("foo.csv").getDataSet();
            instances.setClassIndex(0);
        } catch (Exception e) {
            e.printStackTrace(); // welp
        }

        decisionTree = new J48();
        try {
            decisionTree.buildClassifier(instances);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /** After training the model, use it to find other places that look like "yes" instances.
     * Undefined behavior if you haven't called train() yet. */
    public List<TimePeriod> findOtherHits() {
        List<TimePeriod> hits = new ArrayList<TimePeriod>();
        try {
            for (int i = 0; i < instances.numInstances(); i++) {
                Instance inst = instances.instance(i);
                double result = decisionTree.classifyInstance(inst);
                String resultStr = inst.classAttribute().value((int) result);
                if (resultStr.equals("yes")) {
                    long start = (long) inst.value(10); // TODO magic numbers
                    long end = (long) inst.value(11);

                    hits.add(new SimpleTimePeriod(start, end));
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // weka!
        }
        return hits;
    }

    

    /**
     * Writes features to a dummy CSV file so that you can read it back in with
     * weka.
     */
    private void writeCsvFile(List<TimePeriod> yesTimes, TimeSeriesCollection dataset, String filename) {
        CSVWriter writer = null;
        try {
            writer = new CSVWriter(new FileWriter(filename));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        writer.writeNext(new String[] {"class", "f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "start", "end"}); //TODO
        
        // Generate the Yes instances
        for(TimePeriod yesTime : yesTimes) {
            TimeSeriesCollection subset = makeSubset(dataset, yesTime.getStart(), yesTime.getEnd());
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
        for (TimePeriod selectedTime : yesTimes) {
            long length = selectedTime.getEnd().getTime() - selectedTime.getStart().getTime();
            if (length < shortestInterval)
                shortestInterval = length;
            if (length > longestInterval) 
                longestInterval = length;
        }
        
        List<TimePeriod> unclassifiedTimes = new ArrayList<TimePeriod>();
        List<TimePeriod> noTimes = new ArrayList<TimePeriod>();
        // TODO: this only checks the first time series in the dataset
        for(Object timePeriodObj : dataset.getSeries(0).getTimePeriods()) {
            TimePeriod dataPoint = (TimePeriod) timePeriodObj;
            Date potentialStart = dataPoint.getStart();
            // +1 so it doesn't break if you only have one interval)
            long potentialLength = (long) random.nextInt((int)(longestInterval - shortestInterval) + 1) + shortestInterval;
            Date potentialEnd = new Date(potentialStart.getTime() + potentialLength);
            TimePeriod potentialTimePeriod = new SimpleTimePeriod(potentialStart, potentialEnd);
            if (overlapsAny(potentialTimePeriod, yesTimes)) {
                unclassifiedTimes.add(potentialTimePeriod);
            } else {
                noTimes.add(potentialTimePeriod);
            }
        }
        Collections.shuffle(noTimes);
        unclassifiedTimes = noTimes.subList(yesTimes.size() * 3, noTimes.size());
        noTimes = noTimes.subList(0, yesTimes.size() * 3); // TODO: only gets a few NO instances
        
        for (TimePeriod noTime : noTimes) {
            TimeSeriesCollection subset = makeSubset(dataset, noTime.getStart(), noTime.getEnd());
            List<Object> features = generateFeatures(subset, "no");
            List<String> featuresStr = new ArrayList<String>();
            for (Object feature : features) {
                featuresStr.add(feature.toString());
            }
            writer.writeNext(featuresStr.toArray(new String[featuresStr.size()])); // java!
        }
        
        for (TimePeriod unclassifiedTime : unclassifiedTimes) {
            TimeSeriesCollection subset = makeSubset(dataset, unclassifiedTime.getStart(), unclassifiedTime.getEnd());
            List<Object> features = generateFeatures(subset, "?");
            List<String> featuresStr = new ArrayList<String>();
            for (Object feature : features) {
                featuresStr.add(feature.toString());
            }
            writer.writeNext(featuresStr.toArray(new String[featuresStr.size()])); // java!
            // TODO code duplication!
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
        
        // features 9 and 10: the start and end time of this instance.
        // TODO: I don't think these should really be features, now should they?
        // (I don't want start and end time to get factored into whether this is a Yes or a No!)
        if (data.getSeries(0).getItemCount() > 0) {
            features.add(data.getSeries(0).getTimePeriod(0).getStart().getTime());
            features.add(data.getSeries(0).getTimePeriod(data.getSeries(0).getItemCount() - 1)
                    .getEnd().getTime());
        } else {
            features.add("?");
            features.add("?"); // TODO ugh ugh
        }
        
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
