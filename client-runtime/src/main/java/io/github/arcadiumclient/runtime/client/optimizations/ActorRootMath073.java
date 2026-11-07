package io.github.arcadiumclient.runtime.client.optimizations;

/*
 * Copyright (C) 1993 by Sun Microsystems, Inc. All rights reserved.
 * Developed at SunSoft, a Sun Microsystems, Inc. business.
 * Permission to use, copy, modify, and distribute this software is freely
 * granted, provided that this notice is preserved.
 * Polynomial kernels/constants adapted from FreeBSD lib/msun.
 */
public final class ActorRootMath073 {
    public static final class Decline extends RuntimeException {public final int reason;Decline(int reason){this.reason=reason;}}
    private ActorRootMath073(){}
    public static float checkedFloat(double value,double error) {
        if(!Double.isFinite(value)||!Double.isFinite(error)||error<0)throw new Decline(2);
        if(value==0)return (float)value;
        float candidate=(float)value;
        if(!Float.isFinite(candidate)||candidate==0||Math.abs(candidate)<Float.MIN_NORMAL)throw new Decline(4);
        double low=((double)Math.nextDown(candidate)+candidate)*.5,high=((double)Math.nextUp(candidate)+candidate)*.5;
        if(!(value-error>low&&value+error<high))throw new Decline(4);
        return candidate;
    }
    public static float sin(float angle) {
        if(!Float.isFinite(angle)||Math.abs(angle)>6.2831855f)throw new Decline(3);
        if(angle==0)return angle;
        double x=angle,y0,y1;int n;
        if(Math.abs(x)<=.7853981633974483){n=0;y0=x;y1=0;}
        else {
            int magnitude=(int)Math.floor(Math.abs(x)*6.36619772367581382433e-1+.5);n=x<0?-magnitude:magnitude;
            double r=x-n*1.57079632673412561417,w=n*6.07710050650619224932e-11;
            y0=r-w;y1=(r-y0)-w;
        }
        double value=switch(n&3){case 0->kernelSin(y0,y1,n!=0);case 1->kernelCos(y0,y1);case 2->-kernelSin(y0,y1,true);default->-kernelCos(y0,y1);};
        return checkedFloat(value,Math.abs(value)*0x1.0p-43+0x1.0p-77);
    }
    public static float cosFromSin(float sine,float angle) {
        float square=sine*sine,radicand=1f-square;
        float cosine;
        if(radicand==0)cosine=0f;
        else {
            double y=Math.sqrt((double)radicand);
            double error=Math.abs(Math.fma(-y,y,(double)radicand))/y+Math.abs(y)*0x1.0p-45+0x1.0p-100;
            cosine=checkedFloat(y,error);
        }
        float a=angle+1.5707964f,quotient=a/6.2831855f,turns=(int)quotient*6.2831855f,b=a-turns;
        if(b<0)b=6.2831855f+b;
        return b>=3.1415927f?-cosine:cosine;
    }
    private static double kernelSin(double x,double y,boolean tail) {
        double z=x*x,w=z*z;
        double r=8.33333333332248946124e-3+z*(-1.98412698298579493134e-4+z*2.75573137070700676789e-6)+z*w*(-2.50507602534068634195e-8+z*1.58969099521155010221e-10);
        double v=z*x;
        return tail?x-((z*(.5*y-v*r)-y)-v*(-1.66666666666666324348e-1)):x+v*(-1.66666666666666324348e-1+z*r);
    }
    private static double kernelCos(double x,double y) {
        double z=x*x,w=z*z;
        double r=z*(4.16666666666666019037e-2+z*(-1.38888888888741095749e-3+z*2.48015872894767294178e-5))+w*w*(-2.75573143513906633035e-7+z*(2.08757232129817482790e-9+z*(-1.13596475577881948265e-11)));
        double hz=.5*z,t=1-hz;return t+(((1-t)-hz)+(z*r-x*y));
    }
}
