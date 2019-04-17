import java.io.*;
import java.lang.*;
import java.util.*;


public static int[] BinCount(int arr[]){
    Core.MinMaxLocationResult res = Core.MinMaxLocationResult(arr);
    int max = res.maxVal;
    int h[] = new int[max+1];
    for(int i=0; i<arr.length; i++){
        h[arr[i]] += 1;
    }
    return h;
}

// debubble function
// remove noise
public static MatOfPoint2d debubble (Point[] postiveEdge, Point[] negativeEdge, int win, boolean isLeft){

    int[] countPos = BinCount(postiveEdge.x);
    Core.MinMaxLocationResult temp = Core.MinMaxLocationResult(countPos);
    int postiveEdgeMode = temp.maxLoc;

    int[] countNeg = BinCount(negativeEdge.x);
    Core.MinMaxLocationResult temp = Core.MinMaxLocationResult(countNeg);
    int negativeEdgeMode = temp.maxLoc;

    boolean[] truthArrayPostive = new boolean[postiveEdge.x.length]
    boolean[] truthArrayNegative = new boolean[negativeEdge.x.length]

    for (int i=0; i<postiveEdge.x.length; i++){
        if ((postiveEdge.x[i] < postiveEdgeMode + win) && (postiveEdge.x[i] > postiveEdgeMode - win)){
            truthArrayPostive[i] = true;
        }
    }

    for (int j=0; j<negativeEdge.x.length; j++){
        if((negativeEdge.x[i] < negativeEdgeMode + win) && (negativeEdge.x[i] > negativeEdgeMode - win)){
            truthArrayNegative[i] = true;
        }
    }

}
