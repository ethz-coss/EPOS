/*
 * Copyright (C) 2016 Evangelos Pournaras
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package agents.fitnessFunction.costFunction;

import agents.plan.Plan;
import java.util.Random;

/**
 *
 * @author Peter
 */
public class QuadraticCostFunction implements CostFunction {
    
    private double[][] A;
    private double[] B;

    @Override
    public double calcCost(Plan plan, Plan costSignal) {
        Plan p = plan.clone();
        p.add(costSignal);
        plan = p;
        
        int n = plan.getNumberOfStates();
        if(A == null) {
            Random rand = new Random();
            A = new double[n][n];
            B = new double[n];
            
            for(int i=0; i<n; i++) {
                for(int j=0; j<n; j++) {
                    //A[i][j] = -1/n; // min var
                    //A[i][j] = (((i+j)%2)==0?1:-1)*rand.nextDouble(); // 1'*A*1 == 0
                    A[i][j] = rand.nextGaussian();
                }
                //A[i][i] += 1; // min var
                B[i] = rand.nextDouble();
            }
            
            double[][] AA = new double[n][n];
            for(int i=0; i<n; i++) {
                for(int j=0; j<n; j++) {
                    for(int k=0; k<n; k++) {
                        AA[i][j] -= A[i][k]*A[k][j];
                    }
                }
            }
            //A = AA; // convexify
            
            /*
            System.out.println("A = [");
            for(int i=0; i<n; i++) {
                for(int j=0; j<n; j++) {
                    System.out.print(A[i][j] + (j==n-1?"":","));
                }
                System.out.println((i==n-1?"":";"));
            }
            System.out.println("]");*/
        }
        double v = 0;
        for(int i=0; i<n; i++) {
            for(int j=0; j<n; j++) {
                v += A[i][j]*plan.getValue(i)*plan.getValue(j);
            }
            v += B[i]*plan.getValue(i);
        }
        return v;
    }

    @Override
    public String toString() {
        return "QuadraticCost";
    }

    @Override
    public String getMetric() {
        return "cost";
    }
    
}