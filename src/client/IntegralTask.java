/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package client;

import java.util.concurrent.Callable;

/**
 *
 * @author admin
 */
public class IntegralTask implements Callable<Double> {

    private double a, b, h;
    private double result;

    public IntegralTask(double a, double b, double h) {
        this.a = a;
        this.b = b;
        this.h = h;
    }

    @Override
    public Double call() throws Exception {
        double sum = 0;
        for (double x = a; x < b; x += h) {
            double x2 = Math.min(x + h, b);
            sum += (1.0 / x + 1.0 / x2) / 2.0 * (x2 - x);
        }
        return sum;
    }
    
}
