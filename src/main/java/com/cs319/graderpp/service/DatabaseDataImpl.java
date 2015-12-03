package com.cs319.graderpp.service;

import com.cs319.graderpp.models.*;
import com.mongodb.*;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import javax.print.Doc;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import static java.util.Arrays.asList;

/**
 * Created by yusuf on 25-Nov-15.
 */

@Service(value = "graderppService")
public class DatabaseDataImpl implements DataServiceImpl {

    private MongoCollection<Document> _user_list, _course_list, _task_list, _submission_list;
    private  String[] _collection_keys = {"user_list", "course_list", "task_list", "submission_list"};
    private String[] _user_keys = {"_id", "name", "password", "full_name", "user_type",
            "course_list", "task_list", "submission_list"};
    private String[] _course_keys = {"_id", "name", "code", "instructor_id"};
    private String[] _task_keys = {"_id", "name", "course_id", "ta_id", "due_date"};
    private String[] _submission_keys = {"_id", "task_id", "student_id","due_date"};
    public enum MongoUserType {student, ta, instructor};
    public enum MongoModelType{course, task, submission};



    private void clearCollections(){
        Document d = new Document();
        _user_list.deleteMany(d);
        _course_list.deleteMany(d);
        _submission_list.deleteMany(d);
        _task_list.deleteMany(d);
    }

    private void generateDemoUsers() {

        String id;

        id = addUser("stu", "123", "student demo", MongoUserType.student);
        System.out.println("user instantiated with id: " + id);

        id = addUser("ta", "123", "ta demo", MongoUserType.ta);
        System.out.println("user instantiated with id: " + id);

        id = addUser("inst", "123", "instructor demo", MongoUserType.instructor);
        System.out.println("user instantiated with id: " + id);

    }

    private void generateDemoCourses(String inst_id){
        addCourse(inst_id, "demo1", "demo 101");
        addCourse(inst_id, "demo2", "demo 102");
        addCourse(inst_id, "demo3", "demo 103");
    }

    private void deleteDemoCourses(String user_id){
        deleteCourse(user_id, "demo1", "demo 101");
        deleteCourse(user_id, "demo2", "demo 102");
        deleteCourse(user_id, "demo3", "demo 103");
    }

    private void generateDemoTasks(){

        Document ta_doc = new Document(_user_keys[1], "ta");

        FindIterable<Document> iterable = _user_list.find(ta_doc);
        String ta_id = iterable.first().getObjectId("_id").toHexString();
        String course_id = _course_list.find(new Document()).first().getObjectId("_id").toHexString();

        addTask("task1", course_id, ta_id, "30.12.2015");
        addTask("task2", course_id, ta_id, "30.12.2015");
        addTask("task3", course_id, ta_id, "30.12.2015");
    }

    private void deleteDemoTasks(){
        String task_id = _task_list.find(new Document()).first().getObjectId("_id").toHexString();

        deleteTask(task_id);
    }

    private void generateDemoSubmissions(String student_id){
        String task_id = _task_list.find(new Document()).first().getObjectId("_id").toHexString();

        addSubmission(task_id, student_id);
        addSubmission(task_id, student_id);
        addSubmission(task_id, student_id);
    }

    private void deleteDemoSubmissions(String student_id){

        FindIterable<Document> iterable = _submission_list.find(new Document());

        for (Document doc : iterable){
            deleteSubmission(doc.getObjectId("_id").toHexString(), student_id);
        }
    }

    private void subscribe2CourseDemo(){
        Document student_doc = new Document(_user_keys[1], "stu");
        Document student = _user_list.find(student_doc).first();

        Document course = _course_list.find(new Document()).first();

        subscribe2Course(student.getObjectId("_id").toHexString(), course.getObjectId("_id").toHexString());
    }

    private String addUser(String name, String pass, String full_name, MongoUserType user_type){
        if (getSignedUser(name, pass) != null){
            return "USER ALREADY EXISTS";
        }

        Document new_user = new Document();
        new_user.append(_user_keys[1], name);
        new_user.append(_user_keys[2], pass);
        new_user.append(_user_keys[3], full_name);
        new_user.append(_user_keys[4], MongoUserType2string(user_type));
        new_user.append(_user_keys[5], asList());
        new_user.append(_user_keys[6], asList());
        new_user.append(_user_keys[7], asList());

        _user_list.insertOne(new_user);
        return new_user.getObjectId("_id").toHexString();

    }

    private boolean deleteUser(String user_id){

        return true;
    }

    private String addCourse(String inst_id, String name, String code){
        Document new_course = new Document();

        new_course.append(_course_keys[1], name);
        new_course.append(_course_keys[2], code);
        new_course.append(_course_keys[3], inst_id);

        FindIterable<Document> iterable = _course_list.find(new_course);
        if (iterable.first() != null){  // already have this course
            return null;
        }

        //insert
        _course_list.insertOne(new_course);

        //subscribe to the newly generated course
        String course_id = new_course.getObjectId("_id").toHexString();
        subscribe2Course(inst_id, course_id);
        return course_id;
    }

    private boolean deleteCourse(String user_id, String name, String code){
        Document dead_course_doc = new Document();

        dead_course_doc.append(_course_keys[1], name);
        dead_course_doc.append(_course_keys[2], code);

        FindIterable<Document> iterable = _course_list.find(dead_course_doc);
        Document dead_course = iterable.first();

        if (dead_course == null){  //do not have that course
            return false;
        }

        //delete
        String course_id = dead_course.getObjectId("_id").toHexString();

        //unsubscribe from newly deleted course
        _course_list.deleteOne(dead_course);

        //if anybody takes that course than delete course_id from his course list
        Document scope_doc = getDocFromScope(7); // delete it from everybody
        return unsubscribeAllFromModel(course_id, MongoModelType.course, course_id, MongoModelType.course, scope_doc);
    }

    private String addTask(String name, String course_id, String ta_id, String due_date){
        Document new_task = new Document();

        new_task.append(_task_keys[1], name);
        new_task.append(_task_keys[2], course_id);
        new_task.append(_task_keys[3], ta_id);
        new_task.append(_task_keys[4], due_date);

        FindIterable<Document> iterable = _task_list.find(new_task);
        if (iterable.first() != null){  // already have this task
            return null;
        }
        //insert
        _task_list.insertOne(new_task);
        String task_id = new_task.getObjectId("_id").toHexString();

        //ta should get subscribed to the course that he assigned by inst
        subscribe2Course(ta_id, course_id);

        //if anybody takes that course than subscribe to the tasks of that course
        Document scope_doc = getDocFromScope(7); //ta, student, inst
        subscribeAll2Model(task_id, MongoModelType.task, course_id, MongoModelType.course, scope_doc);
        return task_id;
    }

    private boolean deleteTask(String task_id){

        Document dead_task_doc = new Document();
        dead_task_doc.append(_task_keys[0], new ObjectId(task_id));

        FindIterable<Document> iterable = _task_list.find(dead_task_doc);
        Document dead_task = iterable.first();

        if (dead_task == null){
            return false;
        }
        _task_list.deleteOne(dead_task);

        //if task_list of a user contains that task than delete it
        Document scope_doc = getDocFromScope(7); // delete it from everybody
        return unsubscribeAllFromModel(task_id, MongoModelType.task, task_id, MongoModelType.task, scope_doc);
    }

    private boolean addSubmission(String task_id, String student_id){

        Document new_submission = new Document();
        new_submission.append(_submission_keys[1], task_id);
        new_submission.append(_submission_keys[2], student_id);

        FindIterable<Document> iterable = _submission_list.find(new_submission);
        if (iterable.first() != null){  // already have this submission
            return false;
        }
        //insert
        _submission_list.insertOne(new_submission);
        String submission_id = new_submission.getObjectId("_id").toHexString();

        //student should get subscribed to the submission that he made
        subscribe2Submission(student_id, submission_id);

        Document scope_doc = getDocFromScope(6);
        //if a instructor or a ta have that task, then he should have that submission
        return subscribeAll2Model(submission_id, MongoModelType.submission, task_id, MongoModelType.task, scope_doc);
    }

    private boolean deleteSubmission(String submission_id, String student_id){
        Document dead_submission_doc = new Document();
        dead_submission_doc.append(_submission_keys[0], new ObjectId(submission_id));

        FindIterable<Document> iterable = _submission_list.find(dead_submission_doc);
        Document dead_submission = iterable.first();

        if (dead_submission == null){
            return false;
        }
        _submission_list.deleteOne(dead_submission);

        //student should get unsubscribed from the submission that he made
        unsubscribeFromSubmission(student_id, submission_id);

        //if submission_list of a user contains that submission than delete it
        Document scope_doc = getDocFromScope(6); // delete it from ta and instructor
        return unsubscribeAllFromModel(submission_id, MongoModelType.submission,
                submission_id, MongoModelType.submission, scope_doc);
    }

    private boolean subscribeAll2Model(String new_id, MongoModelType add_coll,
                                       String query_id, MongoModelType query_coll, Document scope_doc){

        int query_ind = getModelIndFromType(query_coll);
        int add_ind = getModelIndFromType(add_coll);

        //subscribe to the newly generated course
        FindIterable<Document> iterable1 = _user_list.find(scope_doc);

        for (Document curr_user : iterable1){
            String user_id = curr_user.getObjectId("_id").toHexString();
            if (inCustomList(user_id, query_id, query_ind)){
                subscribe2List(user_id, new_id, add_ind);
            }
        }

        return true;
    }

    private boolean unsubscribeAllFromModel(String dead_id, MongoModelType del_coll,
                                            String query_id, MongoModelType query_coll, Document scope_doc ){

        int query_ind = getModelIndFromType(query_coll);
        int delete_ind = getModelIndFromType(del_coll);

        FindIterable<Document> iterable1 = _user_list.find(scope_doc);
        for (Document curr_user : iterable1){
            String user_id = curr_user.getObjectId("_id").toHexString();
            if (inCustomList(user_id, query_id, query_ind)){
                unsubscribeFromList(user_id, dead_id, delete_ind);
            }
        }
        return true;
    }

    //inst, ta, student (111) means query for inst, ta, and student types, (binary FORMAT!)
    private Document getDocFromScope(int scope){
        Document student_doc = new Document(_user_keys[4], MongoUserType2string(MongoUserType.student));
        Document ta_doc = new Document(_user_keys[4], MongoUserType2string(MongoUserType.ta));
        Document inst_doc = new Document(_user_keys[4], MongoUserType2string(MongoUserType.instructor));

        Document r = new Document();

        switch (scope){
            case 1:
                r = student_doc;
                return r;
            case 2:
                r = ta_doc;
                return r;
            case 3:
                r.append("$or", asList(student_doc, ta_doc));
                return r;
            case 4:
                r = inst_doc;
                return r;
            case 5:
                r.append("$or", asList(student_doc, inst_doc));
                return r;
            case 6:
                r.append("$or", asList(ta_doc, inst_doc));
                return r;
            case 7:
                r.append("$or", asList(student_doc, ta_doc, inst_doc));
                return r;
        }


        return r;
    }

    private int getModelIndFromType(MongoModelType m){
        switch (m){
            case course:
                return 5;
            case task:
                return 6;
            case submission:
                return 7;
            default:
                return 5;
        }
    }

    private boolean inCustomList(String user_id, String elem_id, int list_ind){
        List l = getList4User(user_id, list_ind);
        return l.contains(elem_id);
    }

    private boolean subscribe2List(String user_id, String elem_id, int list_ind){
        List l = getList4User(user_id, list_ind);
        l.add(elem_id);

        Document curr_user = new Document().append("_id", new ObjectId(user_id));
        UpdateResult ur = _user_list.updateOne(curr_user,
                new Document("$set", new Document(_user_keys[list_ind], l)));

        return true;
    }

    private boolean unsubscribeFromList(String user_id, String elem_id, int list_ind){
        List l = getList4User(user_id, list_ind);
        l.remove(elem_id);

        Document curr_user = new Document().append("_id", new ObjectId(user_id));
        UpdateResult ur = _user_list.updateOne(curr_user,
                new Document("$set", new Document(_user_keys[list_ind], l)));

        return true;
    }

    private boolean subscribe2Course(String user_id, String course_id){
        return subscribe2List(user_id, course_id, 5);
    }

    private boolean unsubscribeFromCourse(String user_id, String course_id){
        return unsubscribeFromList(user_id, course_id, 5);
    }

    private boolean subscribe2Task(String user_id, String task_id){
        return subscribe2List(user_id, task_id, 6);
    }

    private boolean unsubscribeFromTask(String user_id, String task_id){
        return unsubscribeFromList(user_id, task_id, 6);
    }

    private boolean subscribe2Submission(String user_id, String submission_id){
        return subscribe2List(user_id, submission_id, 7);
    }

    private boolean unsubscribeFromSubmission(String user_id, String submission_id){
        return unsubscribeFromList(user_id, submission_id, 7);
    }

    private  List getCourses4User(String user_id){ return getList4User(user_id, 5); }

    private List getTasks4User(String user_id){ return getList4User(user_id, 6); }

    private List getSubmissions4User(String user_id){ return getList4User(user_id, 7); }

    private List getList4User(String user_id, int list_ind){

        List l = asList();
        Document user = new Document();
        user.append(_user_keys[0], new ObjectId(user_id));

        FindIterable<Document> iterable = _user_list.find(user);
        Document the_user = iterable.first();
        if (the_user == null ){
            return l;
        }
        l = (List)the_user.get(_user_keys[list_ind]);

        return l;
    }

    private ArrayList<String> getUserList(MongoUserType m){
        ArrayList<String> l = new ArrayList<String>();

        FindIterable<Document> iterable = _user_list.find(
                new Document(_user_keys[4], MongoUserType2string(m)));

        for (Document d : iterable){
            String s = d.getObjectId(_user_keys[0]).toHexString();
            l.add(s);
        }

        return l;
    }

    private ArrayList<String> getModelList(MongoModelType m){
        ArrayList<String> l = new ArrayList<String>();
        FindIterable<Document> coll;
        Document d = new Document();

        switch (m){

            case course:
                coll = _course_list.find(d);
                break;
            case task:
                coll = _task_list.find(d);
                break;
            case submission:
                coll = _submission_list.find(d);
                break;
            default:
                coll = _course_list.find(d);
        }

        for(Document doc : coll){
            l.add(doc.getObjectId("_id").toHexString());
        }

        return l;
    }

    private Date str2date(String str){
        Date d = new Date();

        return d;
    }

    private String MongoUserType2string(MongoUserType mut){
        switch (mut) {
            case student:
                return "stu";

            case ta:
                return "ta";

            case instructor:
                return "inst";

            default:
                return "stu";
        }
    }

    private MongoUserType String2MongoUserType(String type)
    {
        if(type.equals("stu"))
            return MongoUserType.student;
        if(type.equals("ta"))
            return MongoUserType.ta;
        if(type.equals("inst"))
            return MongoUserType.instructor;

        return null;
    }


//-------------------------------------------------------------------------------------------------------------------
//                    interface methods which are public

    public User getSignedUser(String name, String pass){
        Document user_doc = new Document();
        user_doc.append(_user_keys[1], name);
        user_doc.append(_user_keys[2], pass);

        FindIterable<Document> iterable = _user_list.find(user_doc);
        Document d = iterable.first();

        if (d == null){
            return null;
        }

        User user = null;
        switch (String2MongoUserType( d.getString(_user_keys[4])))
        {
            case instructor:
                user = new Instructor(d.getString(_user_keys[1]), d.getString(_user_keys[2]),
                        d.getString(_user_keys[3]));
                break;
            case student:
                user = new Student(d.getString(_user_keys[1]), d.getString(_user_keys[2]),
                        d.getString(_user_keys[3]));
                break;
            case ta:
                user = new Assistant(d.getString(_user_keys[1]), d.getString(_user_keys[2]),
                        d.getString(_user_keys[3]));
                break;
            default:
                break;
        }

        user.setUserId(d.getObjectId(_user_keys[0]).toHexString()); //the most important value

        /*
        generateDemoCourses(u.getUserId(), u.getUserId());
        subscribe2CourseDemo();
        generateDemoTasks();

        List l = getModelList(MongoModelType.task);
        l = getModelList(MongoModelType.course);
        l = getModelList(MongoModelType.submission);
        l = getCourseList();
        l = getSubmissionList();
        l = getTaskList();
        */
        //generateDemoSubmissions(u.getUserId());
        //deleteDemoSubmissions(u.getUserId());

        return user;
    }

    public DatabaseDataImpl(){
        MongoClient mongoClient = new MongoClient("localhost");
        MongoDatabase db = mongoClient.getDatabase("db0");
        _user_list = db.getCollection(_collection_keys[0]);
        _course_list = db.getCollection(_collection_keys[1]);
        _task_list = db.getCollection(_collection_keys[2]);
        _submission_list = db.getCollection(_collection_keys[3]);

        //generateDemoUsers();
        //generateDemoCourses("565f83da8de32a34b452c21c");
        //generateDemoTasks();
        generateDemoSubmissions("565f83da8de32a34b452c21a");
        subscribe2CourseDemo();
        //System.out.println("collections created or connected successfuly");
        /*

        deleteDemoTasks();

        clearCollections();
        generateDemoUsers();
        */
    }


    public void addSubmission(Submission submission) {
        String task_id = submission.getTask().getTaskId();
        String student_id = submission.getSubmitter().getUserId();

        addSubmission(task_id, student_id);
    }

    public Task findTaskById(String taskId) {

        Document task_doc = new Document("_id", new ObjectId(taskId));
        Document task = _task_list.find(task_doc).first();

        String task_name = task.getString(_task_keys[1]);
        String course_id = task.getString(_task_keys[2]);
        String ta_id = task.getString(_task_keys[3]);
        String due_date = task.getString(_task_keys[4]);

        //_task_keys = {"_id", "name", "course_id", "ta_id", "due_date"};
        Task t = new Task();
        t.setTaskName(task_name);
        t.setCourse(findCourseById(course_id));
        t.setAssistant((Assistant) findUserById(ta_id));

        t.setDueDate(null);
        t.setSubmissions(null);
        t.setMakeFiles(null);
        t.setTestCaseFiles(null);

        return t;
    }

    public Course findCourseById(String courseId) {

        Document course_doc = new Document("_id", new ObjectId(courseId));
        Document course = _course_list.find(course_doc).first();

        //_course_keys = {"_id", "name", "code", "instructor_id"};

        String course_name = course.getString(_course_keys[1]);
        String course_code = course.getString(_course_keys[2]);
        String inst_id = course.getString(_course_keys[3]);

        Course c = new Course();
        c.setCourseCode(course_code);
        c.setCourseName(course_name);

        c.setInstructors(null);
        c.setStudents(null);
        c.setTasks(null);

        return c;
    }

    public Student findStudentById(String userId) {

        Document student_doc = new Document("_id", new ObjectId(userId));
        Document student = _user_list.find(student_doc).first();

        //_user_keys = {"_id", "name", "password", "full_name", "user_type",
        //"course_list", "task_list", "submission_list"};
        String name = student.getString(_user_keys[1]);
        String pass = student.getString(_user_keys[2]);
        String full_name = student.getString(_user_keys[3]);
        String user_type = student.getString(_user_keys[4]);
        ArrayList<String> course_list = (ArrayList<String>)student.get(_user_keys[5]);
        ArrayList<String> task_list = (ArrayList<String>) student.get(_user_keys[5]);
        ArrayList<String> submission_list = (ArrayList<String>) student.get(_user_keys[5]);

        Student s = new Student(name, pass, full_name);
        s.setUserId(userId);

        s.setCourses(null);
        s.setSubmissions(null);

        return s;
    }

    public User findUserById(String userId) {
        Document user_doc = new Document("_id", new ObjectId(userId));
        Document user = _user_list.find(user_doc).first();

        //_user_keys = {"_id", "name", "password", "full_name", "user_type",
        //"course_list", "task_list", "submission_list"};
        String name = user.getString(_user_keys[1]);
        String pass = user.getString(_user_keys[2]);
        String full_name = user.getString(_user_keys[3]);
        String user_type = user.getString(_user_keys[4]);
        ArrayList<String> course_list = (ArrayList<String>)user.get(_user_keys[5]);
        ArrayList<String> task_list = (ArrayList<String>) user.get(_user_keys[5]);
        ArrayList<String> submission_list = (ArrayList<String>) user.get(_user_keys[5]);

        User u;
        switch(String2MongoUserType(user_type))
        {
            case instructor:
                u = new Instructor(name, pass, full_name);
                break;
            case student:
                u = new Student(name, pass, full_name);
                break;
            case ta:
                u = new Assistant(name, pass, full_name);
                break;
            default:
                u = null;
                break;
        }

        return u;
    }

    public Submission findSubmissionById(String submissionId) {
        Document submission_doc = new Document("_id", new ObjectId(submissionId));
        Document submission = _submission_list.find(submission_doc).first();

        //_submission_keys = {"_id", "task_id", "student_id"};

        String task_id = submission.getString(_submission_keys[1]);
        String student_id = submission.getString(_submission_keys[2]);
        Task t = findTaskById(task_id);

        Student submitter = findStudentById(student_id);

        Submission s = new Submission(submitter, null, t);
        s.setTask(findTaskById(task_id));

        s.setCodeFiles(null);
        s.setEvaluated(false);
        s.setFile(null);
        s.setGrade(-1);
        s.setSubmissionDate(null);

        return s;
    }

    public List<Assistant> findAllAssistants() {
        Document ta_doc = new Document(_user_keys[4], MongoUserType2string(MongoUserType.ta));
        FindIterable<Document> iterable = _user_list.find(ta_doc);

        ArrayList<Assistant> l = new ArrayList<Assistant>();

        for(Document d : iterable){
            String id = d.getObjectId("_id").toHexString();
            User u = findUserById(id);
            Assistant a = new Assistant(u.getUsername(), u.getPassword(), u.getFullName());
            a.setTasks(null);
            l.add(a);
        }
        return l;
    }


    public void updateTask(String taskId, Task newTask) {
        Task t = findTaskById(taskId);
        t = newTask;
    }

    public List<Task> findAllTasksOfUser(User user) {
        return findListOfUser(user, 6);
    }

    public List<Submission> findSubmissionsOfUser(User user){
        return findListOfUser(user, 7);
    }

    public List<Course> findCoursesOfUser(User user){
        return findListOfUser(user, 5);
    }

    public List findListOfUser(User user, int list_ind){
        ArrayList<String> id_list = (ArrayList<String>)getList4User(user.getUserId(), list_ind);

        ArrayList<Object> l = new ArrayList<Object>();

        for (String id : id_list){
            if (list_ind == 5){// list of courses
                l.add(findCourseById(id));
            }
            else if (list_ind == 6){// list of tasks
                l.add(findTaskById(id));
            }
            else if (list_ind == 7){// list of submissions
                l.add(findSubmissionById(id));
            }
        }
        return l;
    }

    public List<Task> findTasksOfCourse(String courseId)
    {
        List<Task> tasks = new ArrayList<Task>();

        Document course = new Document();
        course.append(_course_keys[0], new ObjectId(courseId));

        FindIterable<Document> iterable = _task_list.find(course);

        for (Document d : iterable){
            String task_id = d.getObjectId(_task_keys[0]).toHexString();
            tasks.add( findTaskById(task_id) );
        }

        return tasks;
    }

    public List<Submission> findSubmissionsOfTask(String taskId)
    {
        List<Submission> submissions = new ArrayList<Submission>();

        Document task = new Document();
        task.append(_task_keys[0], new ObjectId(taskId));

        FindIterable<Document> iterable = _submission_list.find(task);

        for (Document d : iterable)
        {
            String submission_id = d.getObjectId(_submission_keys[0]).toHexString();
            submissions.add( findSubmissionById(submission_id));
        }

        return submissions;
    }

    public void addTask(Task task) {
        String name = task.getTaskName();
        String course_id = task.getCourse().getCourseId();
        String ta_id = task.getAssistant().getUserId();
        String due_date = task.getDueDate().toString();

        String task_id = addTask(name, course_id, ta_id, due_date);
        task.setTaskId(task_id);
    }

    public void addCourse(Course course) {
        String inst_id = course.getInstructors().get(0).getUserId();
        String name_course = course.getCourseName();
        String course_code = course.getCourseCode();

        String course_id = addCourse(inst_id, name_course,course_code);
        course.setCourseId(course_id);
    }

     /*private void connDB(){
        MongoClient mongoClient = new MongoClient("localhost");
        MongoDatabase db = mongoClient.getDatabase("db0");
        MongoCollection<Document> coll = db.getCollection("main_list");

        coll.deleteMany(new Document());
        coll.insertOne(new Document("name", "stu1"));
        coll.insertOne(new Document("name", "stu2"));
        coll.insertOne(new Document("name", "stu3"));

        FindIterable<Document> iterable = db.getCollection("main_list").find();

        iterable.forEach(new Block<Document>() {
            public void apply(final Document document) {
                System.out.println(document);
            }
        });
        System.out.println( iterable.first().toString());
    }*/
}