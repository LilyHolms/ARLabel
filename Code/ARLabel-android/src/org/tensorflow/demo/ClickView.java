package org.tensorflow.demo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class ClickView extends View {
    Paint mypaint=new Paint();
    public ClickView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mypaint.setColor(Color.RED);
        mypaint.setStyle(Paint.Style.FILL);
        mypaint.setStrokeWidth(20);
    }

    //lily
    public static float[] clickCenter = {-1,-1};
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                clickCenter[0]=ev.getX();
                clickCenter[1]=ev.getY();
                invalidate();
                Log.d("TAG", "ACTION_DOWN");
                System.out.println("lily touchevent X:"+ev.getX()+" "+ev.getY());
                break;
            case MotionEvent.ACTION_MOVE:
                Log.d("TAG", "ACTION_MOVE");
                break;
            case MotionEvent.ACTION_UP:
                Log.d("TAG", "ACTION_UP");
                break;
        }
        return true;
    }

    @Override
    public void onDraw(Canvas canvas) {
        // TODO Auto-generated method stub
        super.onDraw(canvas);
        if(clickCenter != null && clickCenter[0] > 0 && clickCenter[1] > 0)
        canvas.drawPoint(clickCenter[0],clickCenter[1],mypaint);
    }

    public void reset(){
        ClickView.clickCenter[0]=-1f;
        ClickView.clickCenter[1]=-1f;
        invalidate();
    }
}
