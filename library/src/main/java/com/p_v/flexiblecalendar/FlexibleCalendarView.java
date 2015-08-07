package com.p_v.flexiblecalendar;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.DrawableRes;
import android.support.annotation.IntDef;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.GridView;
import android.widget.LinearLayout;

import com.antonyt.infiniteviewpager.InfinitePagerAdapter;
import com.p_v.flexiblecalendar.entity.SelectedDateItem;
import com.p_v.flexiblecalendar.view.BaseCellView;
import com.p_v.flexiblecalendar.view.impl.DateCellViewImpl;
import com.p_v.flexiblecalendar.view.impl.WeekdayCellViewImpl;
import com.p_v.fliexiblecalendar.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Calendar;
import java.util.List;

/**
 * A Flexible calendar view
 *
 * @author p-v
 */
public class FlexibleCalendarView extends LinearLayout implements
        FlexibleCalendarGridAdapter.OnDateCellItemClickListener,
        FlexibleCalendarGridAdapter.MonthEventFetcher {

    /**
     * Customize Calendar using this interface
     */
    public interface ICalendarView {
        /**
         * Cell view for the month
         *
         * @param position
         * @param convertView
         * @param parent
         * @return
         */
        BaseCellView getCellView(int position, View convertView, ViewGroup parent);

        /**
         * Cell view for the weekday in the header
         *
         * @param position
         * @param convertView
         * @param parent
         * @return
         */
        BaseCellView getWeekdayCellView(int position, View convertView, ViewGroup parent);
    }

    /**
     * Event Data Provider used for displaying events for a particular date
     */
    public interface EventDataProvider {
        List<Integer> getEventsForTheDay(int year,int month, int day);
    }

    /**
     * Listener for month change.
     */
    public interface OnMonthChangeListener{
        /**
         * Called whenever there is a month change
         * @param year the selected month's year
         * @param month the selected month
         * @param direction  LEFT or RIGHT
         */
        void onMonthChange(int year, int month, @Direction int direction);
    }

    /**
     * Click listener for date cell
     */
    public interface OnDateClickListener{
        /**
         * Called whenever a date cell is clicked
         * @param day selected day
         * @param month selected month
         * @param year selected year
         */
        void onDateClick(int year,int month, int day);
    }

    /**
     * Default calendar view for internal usage
     */
    private class DefaultCalendarView implements ICalendarView {

        @Override
        public BaseCellView getCellView(int position, View convertView, ViewGroup parent) {
            BaseCellView cellView = (BaseCellView) convertView;
            if(cellView == null){
                LayoutInflater inflater = LayoutInflater.from(context);
                cellView = (BaseCellView)inflater.inflate(R.layout.base_cell_layout,null);
            }
            return cellView;
        }

        @Override
        public BaseCellView getWeekdayCellView(int position, View convertView, ViewGroup parent) {
            BaseCellView cellView = (BaseCellView) convertView;
            if(cellView == null){
                LayoutInflater inflater = LayoutInflater.from(context);
                cellView = (BaseCellView)inflater.inflate(R.layout.base_cell_layout,null);
            }
            return cellView;
        }
    }

    /*
     * Direction Constants
     */
    public static final int RIGHT = 0;
    public static final int LEFT = 1;

    private InfinitePagerAdapter monthInfPagerAdapter;
    private WeekdayNameDisplayAdapter weekdayDisplayAdapter;
    private MonthViewPagerAdapter monthViewPagerAdapter;

    /**
     * Direction for movement of FlexibleCalendarView left or right
     */
    @IntDef({LEFT,RIGHT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Direction{}

    private Context context;
    /**
     * View pager for the month view
     */
    private MonthViewPager monthViewPager;
    private GridView weekDisplayView;

    private OnMonthChangeListener onMonthChangeListener;
    private OnDateClickListener onDateClickListener;

    private EventDataProvider eventDataProvider;
    private ICalendarView calendarView;

    private int startDisplayYear;
    private int startDisplayMonth;
    private int startDisplayDay;
    private int weekdayHorizontalSpacing;
    private int weekdayVerticalSpacing;
    private int monthDayHorizontalSpacing;
    private int monthDayVerticalSpacing;
    private int monthViewBackground;
    private int weekViewBackground;

    /**
     * Currently selected date item
     */
    private SelectedDateItem selectedDateItem;

    private int lastPosition;

    public FlexibleCalendarView(Context context){
        super(context);
        this.context = context;
    }

    public FlexibleCalendarView(Context context, AttributeSet attrs){
        super(context,attrs);
        this.context = context;
        init(attrs);
    }

    public FlexibleCalendarView(Context context, AttributeSet attrs, int defStyleAttr){
        super(context, attrs, defStyleAttr);
        this.context = context;
        init(attrs);
    }

    private void init(AttributeSet attrs){
        setAttributes(attrs);
        setOrientation(VERTICAL);

        //initialize the default calendar view
        calendarView = new DefaultCalendarView();

        //create week view header
        weekDisplayView = new GridView(context);
        weekDisplayView.setLayoutParams(
                new GridView.LayoutParams(GridView.LayoutParams.MATCH_PARENT, GridView.LayoutParams.WRAP_CONTENT));
        weekDisplayView.setNumColumns(7);
        weekDisplayView.setHorizontalSpacing(weekdayHorizontalSpacing);
        weekDisplayView.setVerticalSpacing(weekdayVerticalSpacing);
        weekDisplayView.setColumnWidth(GridView.STRETCH_COLUMN_WIDTH);
        weekDisplayView.setBackgroundResource(weekViewBackground);
        weekdayDisplayAdapter = new WeekdayNameDisplayAdapter(getContext(), android.R.layout.simple_list_item_1);

        //setting default week cell view
        weekdayDisplayAdapter.setCellView(new WeekdayCellViewImpl(calendarView));

        weekDisplayView.setAdapter(weekdayDisplayAdapter);
        this.addView(weekDisplayView);

        //setup month view
        monthViewPager = new MonthViewPager(context);
        monthViewPager.setBackgroundResource(monthViewBackground);
        monthViewPager.setNumOfRows(FlexibleCalendarHelper.getNumOfRowsForTheMonth(startDisplayYear, startDisplayMonth));
        monthViewPagerAdapter = new MonthViewPagerAdapter(context, startDisplayYear, startDisplayMonth, this);
        monthViewPagerAdapter.setMonthEventFetcher(this);
        monthViewPagerAdapter.setSpacing(monthDayHorizontalSpacing,monthDayVerticalSpacing);

        //set the default cell view
        monthViewPagerAdapter.setCellViewDrawer(new DateCellViewImpl(calendarView));

        monthInfPagerAdapter = new InfinitePagerAdapter(monthViewPagerAdapter);
        //Initializing with the offset value
        lastPosition = monthInfPagerAdapter.getRealCount() * 100;
        monthViewPager.setAdapter(monthInfPagerAdapter);
        monthViewPager.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        monthViewPager.addOnPageChangeListener(new MonthChangeListener());

        //initialize with the current selected item
        selectedDateItem = new SelectedDateItem(startDisplayYear,startDisplayMonth,startDisplayDay);
        monthViewPagerAdapter.setSelectedItem(selectedDateItem);

        this.addView(monthViewPager);
    }

    private void setAttributes(AttributeSet attrs) {
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.FlexibleCalendarView);
        try {
            Calendar cal = Calendar.getInstance(FlexibleCalendarHelper.getLocale(context));
            startDisplayMonth = a.getInteger(R.styleable.FlexibleCalendarView_startDisplayMonth,cal.get(Calendar.MONTH));
            startDisplayYear = a.getInteger(R.styleable.FlexibleCalendarView_startDisplayYear, cal.get(Calendar.YEAR));
            startDisplayDay = cal.get(Calendar.DAY_OF_MONTH);

            weekdayHorizontalSpacing = (int)a.getDimension(R.styleable.FlexibleCalendarView_weekDayHorizontalSpacing, 0);
            weekdayVerticalSpacing = (int)a.getDimension(R.styleable.FlexibleCalendarView_weekDayVerticalSpacing, 0);
            monthDayHorizontalSpacing = (int)a.getDimension(R.styleable.FlexibleCalendarView_monthDayHorizontalSpacing, 0);
            monthDayVerticalSpacing = (int)a.getDimension(R.styleable.FlexibleCalendarView_monthDayVerticalSpacing,0);

            monthViewBackground = a.getResourceId(R.styleable.FlexibleCalendarView_monthViewBackground,android.R.color.transparent);
            weekViewBackground = a.getResourceId(R.styleable.FlexibleCalendarView_weekViewBackground,android.R.color.transparent);

        } finally {
            a.recycle();
        }
    }

    private class MonthChangeListener implements ViewPager.OnPageChangeListener{



        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            int direction = position>lastPosition? RIGHT : LEFT;

            //refresh the previous adapter and deselect the item
            monthViewPagerAdapter.getMonthAdapterAtPosition(lastPosition % 4).setSelectedItem(null,true);

            //compute the new SelectedDateItem based on the diffence in postion
            SelectedDateItem newDateItem = computeNewSelectedDateItem(lastPosition - position);

            //the month view pager adater will update here again
            monthViewPagerAdapter.refreshDateAdapters(position % 4, newDateItem);

            //update last position
            lastPosition = position;

            //update the currently selected date item
            FlexibleCalendarGridAdapter adapter = monthViewPagerAdapter.getMonthAdapterAtPosition(position%4);
            selectedDateItem = adapter.getSelectedItem();

            if(onMonthChangeListener!=null){
                //fire on month change event
                startDisplayYear = adapter.getYear();
                startDisplayMonth = adapter.getMonth();
                onMonthChangeListener.onMonthChange(startDisplayYear, startDisplayMonth,direction);
            }

        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }

        private SelectedDateItem computeNewSelectedDateItem(int difference){

            Calendar cal = Calendar.getInstance();
            cal.set(selectedDateItem.getYear(),selectedDateItem.getMonth(),1);
            cal.add(Calendar.MONTH, -difference);

            return new SelectedDateItem(cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH), 1);

        }
    }

    /**
     * Expand the view with animation
     */
    public void expand() {
        measure(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        final int targetHeight = getMeasuredHeight();

        getLayoutParams().height = 0;
        setVisibility(View.VISIBLE);
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                getLayoutParams().height = interpolatedTime == 1
                        ? LayoutParams.WRAP_CONTENT
                        : (int)(targetHeight * interpolatedTime);
                requestLayout();
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        a.setDuration(((int)(targetHeight / getContext().getResources().getDisplayMetrics().density)));
        startAnimation(a);
    }


    /**
     * Collapse the view with animation
     */
    public void collapse(){
        final int initialHeight = this.getMeasuredHeight();
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if(interpolatedTime == 1){
                    setVisibility(View.GONE);
                }else{
                    getLayoutParams().height = initialHeight - (int)(initialHeight * interpolatedTime);
                    requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        a.setDuration(((int)(initialHeight / getContext().getResources().getDisplayMetrics().density)));
        startAnimation(a);
    }

    public void setOnMonthChangeListener(OnMonthChangeListener onMonthChangeListener){
        this.onMonthChangeListener = onMonthChangeListener;
    }

    public void setOnDateClickListener(OnDateClickListener onDateClickListener){
        this.onDateClickListener = onDateClickListener;
    }

    public void setEventDataProvider(EventDataProvider eventDataProvider){
        this.eventDataProvider = eventDataProvider;
    }

   /* /**
     * Set the start display year and month
     * @param year  start year to display
     * @param month  start month to display
     *//*
    public void setStartDisplay(int year,int month){
        //TODO revisit there is something wrong going here things are not selected
        this.startDisplayYear = year;
        this.startDisplayMonth = month;
        invalidate();
        requestLayout();
    }
*/
    @Override
    public void onDateClick(SelectedDateItem selectedItem) {
        this.selectedDateItem = selectedItem;
        if(onDateClickListener!=null) {
            onDateClickListener.onDateClick(selectedItem.getYear(), selectedItem.getMonth(), selectedItem.getDay());
        }
    }

    /**
     * @return currently selected date
     */
    public SelectedDateItem getSelectedDateItem(){
        return selectedDateItem;
    }

    /**
     * Move the selection to the next day
     */
    public void moveToPreviousDate(){
        if(selectedDateItem!=null){
            Calendar cal = Calendar.getInstance();
            cal.set(selectedDateItem.getYear(), selectedDateItem.getMonth(), selectedDateItem.getDay());
            cal.add(Calendar.DATE, -1);

            if(selectedDateItem.getMonth()!=cal.get(Calendar.MONTH)) {
                moveToPreviousMonth();
            }else{
                selectedDateItem.setDay(cal.get(Calendar.DAY_OF_MONTH));
                selectedDateItem.setMonth(cal.get(Calendar.MONTH));
                selectedDateItem.setYear(cal.get(Calendar.YEAR));
                monthViewPagerAdapter.setSelectedItem(selectedDateItem);
            }
        }
    }

    /**
     * Move the selection to the previous day
     */
    public void moveToNextDate(){
        if(selectedDateItem!=null){
            Calendar cal = Calendar.getInstance();
            cal.set(selectedDateItem.getYear(), selectedDateItem.getMonth(), selectedDateItem.getDay());
            cal.add(Calendar.DATE, 1);

            if(selectedDateItem.getMonth()!=cal.get(Calendar.MONTH)){
                moveToNextMonth();
            }else{
                selectedDateItem.setDay(cal.get(Calendar.DAY_OF_MONTH));
                selectedDateItem.setMonth(cal.get(Calendar.MONTH));
                selectedDateItem.setYear(cal.get(Calendar.YEAR));
                monthViewPagerAdapter.setSelectedItem(selectedDateItem);
            }
        }
    }

    @Override
    public List<Integer> getEventsForTheDay(int year, int month, int day) {
        return eventDataProvider == null?
                null : eventDataProvider.getEventsForTheDay(year, month, day);
    }

    /**
     * Set the customized calendar view for the calendar for customizing cells
     * and layout
     * @param calendar
     */
    public void setCalendarView(ICalendarView calendar){
        this.calendarView = calendar;
        monthViewPagerAdapter.getCellViewDrawer().setCalendarView(calendarView);
        weekdayDisplayAdapter.getCellViewDrawer().setCalendarView(calendarView);
    }

    /**
     * Set the background resource for week view
     * @param resourceId
     */
    public void setWeekViewBackgroundResource(@DrawableRes int resourceId){
        this.weekViewBackground = resourceId;
        weekDisplayView.setBackgroundResource(resourceId);
    }

    /**
     * Set background resource for the month view
     * @param resourceId
     */
    public void setMonthViewBackgroundResource(@DrawableRes int resourceId){
        this.monthViewBackground = resourceId;
        monthViewPager.setBackgroundResource(resourceId);
    }

    /**
     * sets weekview header horizontal spacing between weekdays
     * @param spacing
     */
    public void setWeekViewHorizontalSpacing(int spacing){
        this.weekdayHorizontalSpacing = spacing;
        weekDisplayView.setHorizontalSpacing(weekdayHorizontalSpacing);

    }

    /**
     * Sets the weekview header vertical spacing between weekdays
     * @param spacing
     */
    public void setWeekViewVerticalSpacing(int spacing){
        this.weekdayVerticalSpacing = spacing;
        weekDisplayView.setVerticalSpacing(weekdayVerticalSpacing);
    }

    /**
     * Sets the month view cells horizontal spacing
     * @param spacing
     */
    public void setMonthViewHorizontalSpacing(int spacing){
        this.monthDayHorizontalSpacing = spacing;
        monthViewPagerAdapter.setSpacing(monthDayHorizontalSpacing, monthDayVerticalSpacing);
    }

    /**
     * Sets the month view cells vertical spacing
     * @param spacing
     */
    public void setMonthViewVerticalSpacing(int spacing){
        this.monthDayVerticalSpacing = spacing;
        monthViewPagerAdapter.setSpacing(monthDayHorizontalSpacing, monthDayVerticalSpacing);
    }

    /**
     * move to next month
     */
    public void moveToNextMonth(){
        moveToPosition(1);
    }

    /**
     * move to position with respect to current position
     * for internal use
     */
    private void moveToPosition(int position){
        monthViewPager.setCurrentItem(lastPosition + position - monthInfPagerAdapter.getRealCount() * 100, true);
    }

    /**
     * move to previous month
     */
    public void moveToPreviousMonth(){
        moveToPosition(-1);
    }

    /**
     * move the position to the current month
     */
    public void goToCurrentMonth(){
        //check has to go left side or right
        int monthDifference = FlexibleCalendarHelper
                .getMonthDifference(selectedDateItem.getYear(),selectedDateItem.getMonth());

        if(monthDifference!=0){
            moveToPosition(monthDifference);
        }

        /*if(monthDifference>0){
            //move right
            if(monthDifference == 1){
                moveToPosition(1);
            }else if(monthDifference == 2){
                monthViewPager.setCurrentItem((lastPosition - monthInfPagerAdapter.getRealCount() * 100) + 2, true);
            }else{
                //refresh complete adapters
                //TODO

            }
        }else if (monthDifference<0){
            if(monthDifference == -1){
                moveToPosition(-1);
            }else if(monthDifference == -2){
                moveToPosition(-2);
            }else{

                //refresh adapters TODO
            }
        }*/

    }

}
