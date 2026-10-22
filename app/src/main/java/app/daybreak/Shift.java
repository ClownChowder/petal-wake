package app.daybreak;
public final class Shift {
    public final String id, title;
    public final long start, end;
    public final boolean allDay;
    public Shift(String id,String title,long start,long end){this(id,title,start,end,false);}
    public Shift(String id,String title,long start,long end,boolean allDay){this.id=id;this.title=title;this.start=start;this.end=end;this.allDay=allDay;}
}
