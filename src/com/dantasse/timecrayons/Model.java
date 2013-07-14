package com.dantasse.timecrayons;

import java.util.List;

import org.jfree.data.time.TimePeriod;

import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

public class Model {
    
    Instances instances;
    
    public void train(List<TimePeriod> selectedTimes) {
        instances = new Instances("selected times", new FastVector(), 0);
        for (TimePeriod tp : selectedTimes) {
            instances.add(new Instance(1, new double[] {0,1,2}));
        }
        System.out.println("training model");
    }
}
