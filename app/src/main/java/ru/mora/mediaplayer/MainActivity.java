package ru.mora.mediaplayer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;

import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private Button bt_next, bt_r_p,bt_prev;     // кнопки
    private TextView tv_cur, tv_full, tv_name;      // поля вывода
    private SeekBar seekbar;    // полоса состояния
    private RadioButton rb_1_res,rb_n_res, rb_1_dir, rb_n_dir, rb_autor, rb_all;    //переключатели
    private ImageView imageView;        // изображение

    private MediaPlayer mediaPlayer;    // медиаплеер
    private double startTime = 0;   // текущее время проигрывания
    private double finalTime = 0;   // длина мелодии

    private Field[] fields;     // массив мелодий из ресурсов
    private ArrayList<File> fs = new ArrayList<File>();     // массив мелодий из памяти устройства
    private Cursor cur;     // курсор для просмотра мелодий из таблицы всех мелодий устройства
    private int file_index=0;   // текущий индекс мелодий в списках
    private int lenght=0;   // количество всех мелодий

    private Handler myHandler = new Handler();      // управление обновления экрана

    public static final int PERMISSION_EXTERNAL_STORAGE = 1;    // код запроса разрешения чтения файлов из памяти

    // второй потом, обновляющий активность, параллельно проигрыванию музыки
    private Runnable UpdateSongTime = new Runnable() {
        public void run() {
            startTime = mediaPlayer.getCurrentPosition();   // текущее время в милисекундах
            // обновление TextView, которая отображает текущее время мелодии
            tv_cur.setText(String.format("%d min, %d sec",
                    TimeUnit.MILLISECONDS.toMinutes((long) startTime),
                    TimeUnit.MILLISECONDS.toSeconds((long) startTime) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.
                                    toMinutes((long) startTime)))
            );
            seekbar.setProgress((int)startTime);    // обновление строки состояния
            myHandler.postDelayed(this, 100);   // запуск UpdateSongTime.run() каждую секунду
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bt_next = (Button) findViewById(R.id.bt_next);
        bt_r_p = (Button)findViewById(R.id.bt_r_p);
        bt_prev = (Button)findViewById(R.id.bt_prev);

        tv_cur = (TextView)findViewById(R.id.curentTime);
        tv_full = (TextView)findViewById(R.id.fullTime);
        tv_name = (TextView)findViewById(R.id.textView4);

        rb_1_res = (RadioButton)findViewById(R.id.rb_1_res);
        rb_n_res = (RadioButton)findViewById(R.id.rb_n_res);
        rb_1_dir = (RadioButton)findViewById(R.id.rb_1_dir);
        rb_n_dir = (RadioButton)findViewById(R.id.rb_n_dir);
        rb_autor = (RadioButton)findViewById(R.id.rb_autor);
        rb_all = (RadioButton)findViewById(R.id.rb_all);

        seekbar = (SeekBar)findViewById(R.id.seekBar);
        seekbar.setClickable(false);    // запрет на перемещение вручную метки прогресса

        imageView = (ImageView) findViewById(R.id.imageView);
        imageView.setImageResource(R.drawable.music_blue);      // установка картинки из ресурсов по умолчанию

        // получение статуса разрешения на чтение из памяти: разрешено или отказано
        int permissionStatus = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            set_music_1_res();      // если разрешение есть, то установить мелодию
        }
        else {  // иначе запросить у пользователя разрешение
            ActivityCompat.requestPermissions(this,     // эта активность
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},     // список размешений
                    PERMISSION_EXTERNAL_STORAGE);   // код запроса разрешения
        }

        // слушатель на кнопку старта/паузы
        bt_r_p.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaPlayer.isPlaying()){// если мелодия воспроизводилась
                    // то остановить методию
                    mediaPlayer.pause();
                    bt_r_p.setText(R.string.run);
                }
                else {
                    // иначе продолжить воспроизведение
                    run_music();
                    bt_r_p.setText(R.string.pause);
                }
            }
        });

        // слушатель для переключения мелодий - предыдущая мелодия
        bt_prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // если произывается список мелодий из ресурсов
                if (rb_n_res.isChecked()){
                    mediaPlayer.release();  // очистка медиаплеера
                    change_index(-1);   // изменение текущего индекса мелодии в списках
                    set_music_n_res();      // установна новой мелодии в плеер
                    run_music();        // воспроизведение мелодии
                }
                // если произывается список мелодий из папки
                if (rb_n_dir.isChecked()){
                    mediaPlayer.release();
                    change_index(-1);
                    set_music_n_dir();
                    run_music();
                }
                // если произывается список мелодий из папки и всех вложенных папок
                if (rb_autor.isChecked()){
                    mediaPlayer.release();
                    change_index(-1);
                    set_music_autor();
                    run_music();
                }
                // если произываются все мелодии на устройстве
                if (rb_all.isChecked()){
                    mediaPlayer.release();
                    if(cur.getPosition()!=0) {      // изменение текущей мелодии в списках
                        cur.moveToPrevious();
                    }
                    else{
                        cur.moveToPosition(lenght-1);
                    }
                    set_music_all();
                    run_music();
                }
            }
        });
        // слушатель для переключения мелодий - следующая мелодия
        bt_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (rb_n_res.isChecked()){
                    mediaPlayer.release();
                    change_index(1);
                    set_music_n_res();
                    run_music();
                }
                if (rb_n_dir.isChecked()){
                    mediaPlayer.release();
                    change_index(1);
                    set_music_n_dir();
                    run_music();
                }
                if (rb_autor.isChecked()){
                    mediaPlayer.release();
                    change_index(1);
                    set_music_autor();
                    run_music();
                }
                if (rb_all.isChecked()){
                    mediaPlayer.release();
                    if(cur.getPosition()!=lenght-1) {
                        cur.moveToNext();
                    }
                    else{
                        cur.moveToPosition(0);
                    }
                    set_music_all();
                    run_music();
                }
            }
        });
        // слушатель на переключатели
        View.OnClickListener radioButtonClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageView.setImageResource(R.drawable.music_blue);  // установка картинки по умолчанию
                // если мелодия была загружена в медиаплеер, то очистить медиаплеер
                if (mediaPlayer != null){
                    mediaPlayer.release();
                }
                // если проигрывались все мелодии с устройства, то закрыть и очистить курсор
                if (cur != null){
                    cur.close();
                    cur = null;
                }
                // получение переключателя, который был выбран
                RadioButton rb = (RadioButton) v;
                switch (rb.getId()) {
                    // если выбрана одна мелодия из ресурсов
                    case R.id.rb_1_res:
                        set_music_1_res();  // установка мелодии в плеер
                        break;
                    // если выбраны все мелодии из ресурсов
                    case R.id.rb_n_res:
                        get_music_n_res();  // получение списка мелодий из ресурсов
                        set_music_n_res();  // установка первой мелодии в плеер
                        break;
                    // если выбрана одна мелодия из помяти устройства
                    case R.id.rb_1_dir:
                        set_music_1_dir();  // установка мелодии в плеер
                        break;
                    // если выбраны все мелодии в папке
                    case R.id.rb_n_dir:
                        fs = new ArrayList<File>();     // очистка списка
                        get_music_n_dir();  // получение списка мелодий из папки
                        set_music_n_dir();  // установка первой мелодии в плеер
                        break;
                    // если выбрана все мелодии в папке и в вложеных папках
                    case R.id.rb_autor:
                        fs = new ArrayList<File>();     // очистка списка
                        // установка корневой папки
                        String str = String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC));
                        get_music_autor(str);   // получение списка мелодий в папках
                        set_music_autor();  // установка первой мелодии в плеер
                        break;
                    // если выбраны все мелодии устройства
                    case R.id.rb_all:
                        get_music_all();    // получение всех мелодий
                        cur.moveToNext();   // устанавливаем считываение перед первой мелодией
                        set_music_all();    // установка первой мелодии в плеер
                        break;

                    default:
                        break;
                }
            }
        };
        // установка слешателя на все переключатели
        rb_1_res.setOnClickListener(radioButtonClickListener);
        rb_n_res.setOnClickListener(radioButtonClickListener);
        rb_1_dir.setOnClickListener(radioButtonClickListener);
        rb_n_dir.setOnClickListener(radioButtonClickListener);
        rb_autor.setOnClickListener(radioButtonClickListener);
        rb_all.setOnClickListener(radioButtonClickListener);


    }
    private void run_music(){
        mediaPlayer.start();     // воспроизведение мелодии
        myHandler.postDelayed(UpdateSongTime,100);   // запуска паралельного одновление экрана активности
    }

    private void change_index(int it){
        switch (it){
            // если запускаем следующую мелодию
            case 1:
                if (file_index == lenght-1){
                    file_index = 0;
                }
                else{
                    file_index++;
                }
                break;
            // если запускаем предыдущую мелодию
            case -1:
                if (file_index == 0){
                    file_index = lenght-1;
                }
                else{
                    file_index--;
                }
                break;
        }
    }

    private void set_music_1_res(){
        mediaPlayer = MediaPlayer.create(this, R.raw.song); // загрузка мелодии из папки ресурсов
        mediaPlayer.setLooping(true);    // зацикливание мелодии

        update_text_view(); // обновление TextView

        tv_name.setText("Song.mp3");     // установка названия мелодии
    }

    private void get_music_n_res(){
        fields=R.raw.class.getFields(); // получение всех мелодий их ресурса
        file_index=0;   // обнуление индекса
        lenght = fields.length;     // получение количесва мелодий
    }

    private void set_music_n_res() {
        try {
            // загрузка мелодии с индексом из папки ресурсов
            mediaPlayer = MediaPlayer.create(this, fields[file_index].getInt(fields[file_index])); // загрузка трека из папки проекта
        }
        catch (IllegalAccessException e){
            Toast.makeText(getApplicationContext(), "no",Toast.LENGTH_SHORT).show();
        }
        mediaPlayer.setLooping(true);   // зацикливание мелодии

        update_text_view(); // обновление TextView

        tv_name.setText(fields[file_index].getName());     // установка названия мелодии
    }

    private void set_music_1_dir(){
        // получение мелодии из памяти
        File f = new File(Environment.getExternalStorageDirectory()+ "/111.mp3");
        Uri uri1 = Uri.fromFile(f); // получение пути к файлу
        mediaPlayer = new MediaPlayer();    // создание нового медиаплеера
        try {
            mediaPlayer.setDataSource(uri1.toString()); // загружаем мелодию
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);  // установка потока
            mediaPlayer.setLooping(true);   // зацикливание
            mediaPlayer.prepare();  // подготовка к проигрыванию
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "no",Toast.LENGTH_SHORT).show();
        }

        update_text_view(); // обновление TextView

        tv_name.setText("111.mp3");     // установка названия мелодии
    }

    private void get_music_n_dir(){
        // получение папки системной MUSIC
        File dir = new File(String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)));
        File []files = dir.listFiles(); // получение всех файлов в папке
        for(File f: files){ // для каждого файла
            if(!f.isDirectory()){   // если файл не папка
                fs.add(f);  // добавляем мелодию в список
            }
        }
        file_index=0;   // обнуление индекса
        lenght = fs.size();     // получение количесва мелодий
    }

    private void set_music_n_dir(){
        File f = fs.get(file_index); // получаем файл из списка
        Uri uri1 = Uri.fromFile(f);  // получаем путь к файлу
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(uri1.toString()); // загружаем мелодию
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);  // установка потока
            mediaPlayer.prepare();  // подготовка к проигрыванию
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "no",Toast.LENGTH_SHORT).show();
        }
        update_text_view(); // обновление TextView

        // установка названия мелодии
        String[] sp = f.toString().split("/");
        tv_name.setText(sp[sp.length-1]);
    }

    private void get_music_autor(String path){
        File dir = new File(path);  // получаем папку
        File []files = dir.listFiles(); // список файло в папке
        for(File f: files){ // для каждого файла
            if(!f.isDirectory()){
                // если файл не папка
                String[] sp = f.toString().split("\\.");
                if (sp[sp.length-1].equals("mp3")) {
                    //если файл mp3, то добавляем в список
                    fs.add(f);
                }
            }
            else {
                // если файл папка, то смотрим файлы в ней (рекурсия)
                get_music_autor(f.getPath());
            }
        }
        file_index=0;
        lenght = fs.size();
    }

    private void set_music_autor(){
        File f = fs.get(file_index); // получаем файл из списка
        Uri uri1 = Uri.fromFile(f);  // получаем путь к файлу
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(uri1.toString()); // загружаем мелодию
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);  // установка потока
            mediaPlayer.prepare();  // подготовка к проигрыванию
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "no",Toast.LENGTH_SHORT).show();
        }
        update_text_view(); // обновление TextView

        // установка названия мелодии
        String[] sp = f.toString().split("/");
        tv_name.setText(sp[sp.length-1]);
    }

    private void get_music_all(){
        ContentResolver cr = this.getContentResolver(); // контент прилодения
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;  // все мелодии на телефоне
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";    // только мелодии (не рингтоны)
        // запрос к базе данным для получения всех мелодий, возвращает представление таблицы БД,
        // в которой просматриваются записи с помощью курсора
        // аналогия двумерный масссив объектов: строки - это объекты, столбцы - значения полей объекта
        cur = cr.query(uri, null, selection, null, null);
        if(cur != null)
        {
            file_index = 0;
            lenght = cur.getCount();
        }
    }

    private void set_music_all(){
        // получение пути к мелодии
        String data = cur.getString(cur.getColumnIndex(MediaStore.Audio.Media.DATA));
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(data);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.prepare();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "no",Toast.LENGTH_SHORT).show();
        }
        try {
            Bitmap imgAlbumArt; // картинка альбома мелодии
            // загрузка катринки альбома
            MediaMetadataRetriever metaRetriver =new MediaMetadataRetriever();
            int column_index = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            metaRetriver.setDataSource(cur.getString(column_index));
            byte[] art = metaRetriver.getEmbeddedPicture();
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = 2;
            imgAlbumArt = BitmapFactory .decodeByteArray(art, 0, art.length, opt);
            imageView.setImageBitmap(imgAlbumArt);  // установка полученной картинки
        }
        catch (Exception e)
        {
            // если оштбка,  то картинка по умолчанию
            imageView.setImageResource(R.drawable.music_blue);
        }

        update_text_view(); // обновление TextView
        // установка названия мелодии
        String[] sp = data.split("/");
        tv_name.setText(sp[sp.length-1]);

    }

    private void update_text_view(){
        finalTime = mediaPlayer.getDuration();  // время мелодии
        seekbar.setMax((int) finalTime);    // установка длины полосы состояния

        tv_cur.setText(String.format("%d min, %d sec", 0,0 ));   // обнуление времени
        tv_full.setText(String.format("%d min, %d sec",  // установка длины мелодии
                TimeUnit.MILLISECONDS.toMinutes((long) finalTime),
                TimeUnit.MILLISECONDS.toSeconds((long) finalTime) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes((long)finalTime))));

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // обработка ответа пользователя на запрос разрешения
        switch (requestCode) { // код запроса
            case PERMISSION_EXTERNAL_STORAGE:
                if (grantResults.length > 0  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // если пользователь разрешил доступ, то закружаем мелодию
                    set_music_1_res();
                }
                else {
                    Toast.makeText(getApplicationContext(), "no",Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }


}