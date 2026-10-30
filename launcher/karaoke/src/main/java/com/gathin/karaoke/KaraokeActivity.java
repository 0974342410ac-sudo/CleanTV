package com.gathin.karaoke;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main screen, laid out like a karaoke box: a sidebar of pages (home, search, trending, singers, genres,
 * favorites, sung, the chosen list), the page itself, and a "Bài kế" bar at the bottom with the songs waiting and
 * one button to sing the next. OK on a song opens its menu (sing now, add, sing next, favorite); long OK or Menu
 * adds it straight to the list. The list plays on by itself in the built-in player; in the YouTube app, which does
 * not tell when a song ends, "Hát bài kế" starts each next one.
 */
public class KaraokeActivity extends Activity {
    private static final int HOME = 0, SEARCH = 1, TRENDING = 2, SINGERS = 3, GENRES = 4, FAVORITES = 5,
            HISTORY = 6, QUEUE = 7, PHONE = 8;
    private static final int[][] NAV = {
            {R.string.nav_home, R.drawable.ic_home}, {R.string.nav_search, R.drawable.ic_search},
            {R.string.nav_trending, R.drawable.ic_fire}, {R.string.nav_singers, R.drawable.ic_person},
            {R.string.nav_genres, R.drawable.ic_genre}, {R.string.nav_favorites, R.drawable.ic_heart},
            {R.string.nav_history, R.drawable.ic_history}, {R.string.nav_queue, R.drawable.ic_queue},
            {R.string.nav_phone, R.drawable.ic_phone},
    };

    /** A genre tile: its name, what it searches, its colours. */
    static final class Genre {
        final int label;
        final String query;
        final int from, to;

        Genre(int label, String query, int from, int to) {
            this.label = label;
            this.query = query;
            this.from = from;
            this.to = to;
        }
    }

    static final Genre[] GENRE_LIST = {
            new Genre(R.string.genre_bolero, "karaoke bolero", 0xFFB0306A, 0xFF5B1A7A),
            new Genre(R.string.genre_young, "karaoke nhạc trẻ", 0xFF2F6BFF, 0xFF7B3DFF),
            new Genre(R.string.genre_duet, "karaoke song ca", 0xFFFF6A3D, 0xFFC2185B),
            new Genre(R.string.genre_live_band, "karaoke nhạc sống", 0xFF1E9E6A, 0xFF0F5C6B),
            new Genre(R.string.genre_remix, "karaoke remix", 0xFFE91E63, 0xFF3D2DFF),
            new Genre(R.string.genre_nonstop, "karaoke nonstop", 0xFF7C4DFF, 0xFF00B8D4),
            // With the singer's voice, to listen to: no "karaoke" in the search
            new Genre(R.string.genre_nonstop_vocal, "nhạc nonstop có lời", 0xFF00897B, 0xFF7C4DFF),
            new Genre(R.string.genre_trinh, "karaoke nhạc Trịnh Công Sơn", 0xFF8D6E63, 0xFF3E2723),
            new Genre(R.string.genre_medley, "liên khúc karaoke", 0xFFFFA000, 0xFFD84315),
            new Genre(R.string.genre_kids, "karaoke thiếu nhi", 0xFF00BCD4, 0xFF4CAF50),
            new Genre(R.string.genre_female, "karaoke tone nữ", 0xFFFF4F9A, 0xFF9C27B0),
            new Genre(R.string.genre_male, "karaoke tone nam", 0xFF3949AB, 0xFF00838F),
            new Genre(R.string.genre_chinese, "karaoke nhạc Hoa lời Việt", 0xFFD32F2F, 0xFFFFA000),
            new Genre(R.string.genre_folk, "karaoke dân ca quê hương", 0xFF689F38, 0xFF33691E),
    };
    private static final String TRENDING_QUERY = "karaoke";
    private static final int HOME_SINGERS = 14;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SongQueue queue;
    private Library library;
    private Voice voice;
    private List<String> singers;

    /** Page on screen, and the singer or genre whose songs it shows (null: the page's own list). */
    private int page = -1;
    private String sub;

    /** Songs of the grid, the page they came from (for "more"), and the trending page kept for home. */
    private final List<Song> results = new ArrayList<>();
    private SearchParser.Page lastPage, trending;
    private boolean loading;
    /** Bumped by each new search, so a slow answer to an older one is dropped. */
    private int searchId;
    /** The search page keeps its last answer while other pages are shown. */
    private String lastTyped;
    private SearchParser.Page searched;
    /** Home's trending row (the latest one built) and whether its songs are on their way. */
    private SongAdapter trendRow;
    private boolean trendingLoading;
    /** What home's rows were built from; home is rebuilt (and loses its focus) only when it changes. */
    private String homeBuiltFrom;
    private static final int KEEP_NONE = 0, KEEP_TRENDING = 1, KEEP_SEARCH = 2;

    private LinearLayout nav, homeRows, searchBar;
    private TextView pageTitle, status, nowCount, nowNext;
    private EditText search;
    private Button playerButton, nextButton;
    private View home;
    private RecyclerView grid;
    private GridLayoutManager gridLayout;
    private final SongAdapter songAdapter = new SongAdapter(results, false);
    private SingerAdapter singerAdapter;
    private final GenreAdapter genreAdapter = new GenreAdapter(false);
    private final QueueAdapter queueAdapter = new QueueAdapter();
    private final List<SongAdapter> homeSongRows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_karaoke);
        queue = SongQueue.get(this);
        library = Library.get(this);
        singers = Arrays.asList(getResources().getStringArray(R.array.singers));
        singerAdapter = new SingerAdapter(singers, false);
        voice = new Voice(this, text -> {
            search.setText(text);
            runTypedSearch();
        });

        nav = findViewById(R.id.nav);
        homeRows = findViewById(R.id.home_rows);
        searchBar = findViewById(R.id.search_bar);
        pageTitle = findViewById(R.id.page_title);
        status = findViewById(R.id.status);
        nowCount = findViewById(R.id.now_count);
        nowNext = findViewById(R.id.now_next);
        search = findViewById(R.id.search);
        playerButton = findViewById(R.id.player_button);
        nextButton = findViewById(R.id.next_button);
        home = findViewById(R.id.home);
        grid = findViewById(R.id.grid);

        for (int i = 0; i < NAV.length; i++) {
            TextView item = (TextView) getLayoutInflater().inflate(R.layout.item_nav, nav, false);
            item.setText(NAV[i][0]);
            item.setCompoundDrawablesRelativeWithIntrinsicBounds(NAV[i][1], 0, 0, 0);
            int p = i;
            item.setOnClickListener(v -> showPage(p, true));
            nav.addView(item);
        }

        search.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                runTypedSearch();
                return true;
            }
            return false;
        });
        findViewById(R.id.search_button).setOnClickListener(v -> runTypedSearch());
        findViewById(R.id.mic_button).setOnClickListener(v -> voice.start());
        playerButton.setOnClickListener(v -> {
            Player.setExternal(this, !Player.external(this));
            updatePlayerButton();
        });
        nextButton.setOnClickListener(v -> playNext());

        gridLayout = new GridLayoutManager(this, 3);
        grid.setLayoutManager(gridLayout);
        grid.setItemAnimator(null);

        phonePage = findViewById(R.id.phone_page);
        findViewById(R.id.phone_new_pin).setOnClickListener(v -> {
            PhoneRemote.newPin(this);
            showPhone();
            updateNowQr();
        });

        updatePlayerButton();
        showPage(HOME, false);
        nav.getChildAt(HOME).requestFocus();
        PhoneRemote.start(this);
        // A phone changed the list: the bar, the counts and the list page follow
        queue.setListener(this::changed);
    }

    @Override
    protected void onStart() {
        super.onStart();
        PhoneRemote.visible++;
    }

    @Override
    protected void onStop() {
        PhoneRemote.visible--;
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from a song: the list may have moved on, and "Đã hát" has a new song
        updateNowBar();
        updateNowQr();
        refreshPage();
        if (queue.size() > 0 && Player.external(this)) nextButton.requestFocus();
    }

    @Override
    protected void onDestroy() {
        queue.setListener(null);
        voice.stop();
        worker.shutdownNow();
        super.onDestroy();
    }

    // ---- Phone page ----

    private View phonePage;
    /** What the bar's QR code shows now; drawn again only when the address or PIN changed. */
    private String nowQrUrl;

    /** The QR code in the "Bài kế" bar, always on screen so a phone can join any time. */
    private void updateNowQr() {
        String url = PhoneRemote.pairUrl(this);
        ImageView qr = findViewById(R.id.now_qr);
        TextView line = findViewById(R.id.now_phone);
        if (url == null) {
            qr.setVisibility(View.GONE);
            line.setText(R.string.phone_no_network);
            nowQrUrl = null;
            return;
        }
        qr.setVisibility(View.VISIBLE);
        if (!url.equals(nowQrUrl)) qr.setImageBitmap(Qr.bitmap(url, dp(112)));
        nowQrUrl = url;
        // http://192.168.31.147:8687/#pin=1234 -> 192.168.31.147:8687, for typing instead of scanning
        line.setText(getString(R.string.now_phone, url.substring("http://".length(), url.indexOf("/#"))));
    }

    /** The address and PIN as text and as a QR code; scanning it pairs the phone without typing the PIN. */
    private void showPhone() {
        String address = PhoneRemote.address();
        String pin = PhoneRemote.pin(this);
        TextView steps = findViewById(R.id.phone_steps);
        ImageView qr = findViewById(R.id.phone_qr);
        if (address == null) {
            status.setText(R.string.phone_no_network);
            steps.setText(R.string.phone_no_network);
            qr.setImageDrawable(null);
            return;
        }
        status.setText(R.string.phone_sub);
        steps.setText(getString(R.string.phone_steps, address, pin));
        qr.setImageBitmap(Qr.bitmap(address + "/#pin=" + pin, dp(300)));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST) {
            showPage(SEARCH, true);
            voice.start();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Back: up from a singer's or genre's songs, then to the sidebar, then home, then out. */
    @Override
    public void onBackPressed() {
        if (sub != null && (page == SINGERS || page == GENRES)) {
            String was = sub;
            int p = page;
            showPage(p, false);
            int at = p == SINGERS ? singers.indexOf(was) : genreIndex(was);
            grid.scrollToPosition(Math.max(at, 0));
            grid.post(() -> focusGridItem(Math.max(at, 0)));
            return;
        }
        View focus = getCurrentFocus();
        if (focus != null && !isInside(focus, nav)) {
            nav.getChildAt(page).requestFocus();
            return;
        }
        if (page != HOME) {
            showPage(HOME, false);
            nav.getChildAt(HOME).requestFocus();
            return;
        }
        super.onBackPressed();
    }

    private static boolean isInside(View v, ViewGroup group) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) {
            if (p == group) return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Voice.REQUEST_SPEECH) voice.onResult(resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == Voice.REQUEST_PERMISSION) {
            voice.onPermission(grantResults.length > 0 && grantResults[0] == 0);
        }
    }

    // ---- Pages ----

    /** Shows a page from the sidebar. focusContent moves the focus into it (OK on the sidebar). */
    private void showPage(int p, boolean focusContent) {
        page = p;
        sub = null;
        searchId++;              // drops a search still on its way
        loading = false;
        for (int i = 0; i < nav.getChildCount(); i++) nav.getChildAt(i).setSelected(i == p);
        pageTitle.setText(p == QUEUE ? R.string.queue_title : NAV[p][0]);
        searchBar.setVisibility(p == SEARCH ? View.VISIBLE : View.GONE);
        home.setVisibility(p == HOME ? View.VISIBLE : View.GONE);
        grid.setVisibility(p == HOME || p == PHONE ? View.GONE : View.VISIBLE);
        phonePage.setVisibility(p == PHONE ? View.VISIBLE : View.GONE);
        switch (p) {
            case HOME:
                pageTitle.setText(R.string.home_hello);
                status.setText(R.string.home_hint);
                buildHome();
                if (focusContent) home.post(() -> focusFirstIn(homeRows));
                break;
            case SEARCH:
                status.setText(R.string.hint_start);
                if (searched != null) {
                    setSongs(searched.songs, searched);
                    status.setText(getString(R.string.results_count, lastTyped));
                } else {
                    setSongs(new ArrayList<>(), null);
                }
                if (focusContent) search.requestFocus();
                break;
            case TRENDING:
                if (trending != null) {
                    setSongs(trending.songs, trending);
                    status.setText(R.string.trending_sub);
                    if (focusContent) grid.post(() -> focusGridItem(0));
                } else {
                    loadSongs(TRENDING_QUERY, YouTubeSearch.VIDEOS_THIS_MONTH, getString(R.string.trending_sub),
                            focusContent, KEEP_TRENDING);
                }
                break;
            case SINGERS:
                gridLayout.setSpanCount(4);
                grid.setAdapter(singerAdapter);
                status.setText(getString(R.string.singers_sub, singers.size()));
                if (focusContent) grid.post(() -> focusGridItem(0));
                break;
            case GENRES:
                gridLayout.setSpanCount(3);
                grid.setAdapter(genreAdapter);
                status.setText(R.string.genres_sub);
                if (focusContent) grid.post(() -> focusGridItem(0));
                break;
            case PHONE:
                showPhone();
                if (focusContent) findViewById(R.id.phone_new_pin).requestFocus();
                break;
            case FAVORITES:
            case HISTORY:
            case QUEUE:
                refreshPage();
                if (focusContent) grid.post(() -> focusGridItem(0));
                break;
            default:
                break;
        }
    }

    /** Redraws a page whose list lives on the TV (favorites, history, the chosen list, home rows). */
    private void refreshPage() {
        switch (page) {
            case HOME:
                buildHome();
                break;
            case FAVORITES: {
                List<Song> fav = library.favorites();
                setSongs(fav, null);
                status.setText(fav.isEmpty() ? getString(R.string.favorites_empty)
                        : getString(R.string.favorites_sub, fav.size()));
                break;
            }
            case HISTORY: {
                List<Song> sung = library.history();
                setSongs(sung, null);
                status.setText(sung.isEmpty() ? getString(R.string.history_empty)
                        : getString(R.string.history_sub, sung.size()));
                break;
            }
            case QUEUE:
                gridLayout.setSpanCount(1);
                if (grid.getAdapter() != queueAdapter) grid.setAdapter(queueAdapter);
                keepGridFocus(() -> queueAdapter.set(queue.songs()));
                status.setText(queue.size() == 0 ? getString(R.string.queue_empty)
                        : getString(R.string.queue_sub, queue.size()));
                break;
            default:
                songAdapter.notifyItemRangeChanged(0, songAdapter.getItemCount());
                break;
        }
    }

    /**
     * The grid shows these songs; more is the YouTube page they came from (null: no "more"). The same songs again
     * (back from the player, a heart added) only redraw their marks, so the focus stays on its card.
     */
    private void setSongs(List<Song> songs, SearchParser.Page more) {
        lastPage = more;
        gridLayout.setSpanCount(3);
        if (grid.getAdapter() != songAdapter) grid.setAdapter(songAdapter);
        if (songs != results && ids(songs).equals(ids(results))) {
            songAdapter.notifyItemRangeChanged(0, results.size());
            return;
        }
        keepGridFocus(() -> {
            if (songs != results) {
                results.clear();
                results.addAll(songs);
            }
            songAdapter.notifyDataSetChanged();
        });
    }

    /**
     * Runs update, which redraws the grid's items. Android drops the focus of a redrawn item, which then lands on
     * the sidebar or a button; here it goes back to the same place in the grid (or its last item).
     */
    private void keepGridFocus(Runnable update) {
        View f = getCurrentFocus();
        View item = f != null && isInside(f, grid) ? grid.findContainingItemView(f) : null;
        int at = item == null ? -1 : grid.getChildAdapterPosition(item);
        update.run();
        if (at < 0) return;
        grid.post(() -> {
            RecyclerView.Adapter<?> a = grid.getAdapter();
            int n = a == null ? 0 : a.getItemCount();
            if (n == 0) {
                nav.getChildAt(page).requestFocus();
                return;
            }
            int p = Math.min(at, n - 1);
            grid.scrollToPosition(p);
            grid.post(() -> focusGridItem(p));
        });
    }

    private void openSinger(String name) {
        page = SINGERS;
        sub = name;
        pageTitle.setText(name);
        loadSongs(SearchParser.query(name), YouTubeSearch.VIDEOS_ONLY, getString(R.string.singer_songs, name),
                true, KEEP_NONE);
    }

    private void openGenre(Genre g) {
        page = GENRES;
        sub = getString(g.label);
        pageTitle.setText(g.label);
        loadSongs(g.query, YouTubeSearch.VIDEOS_ONLY, getString(R.string.genre_songs, sub), true, KEEP_NONE);
    }

    private int genreIndex(String label) {
        for (int i = 0; i < GENRE_LIST.length; i++) if (getString(GENRE_LIST[i].label).equals(label)) return i;
        return 0;
    }

    private void runTypedSearch() {
        String typed = search.getText().toString().trim();
        if (typed.isEmpty()) {
            status.setText(R.string.hint_start);
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        if (page != SEARCH) showPage(SEARCH, false);
        lastTyped = typed;
        loadSongs(SearchParser.query(typed), YouTubeSearch.VIDEOS_ONLY, getString(R.string.results_count, typed),
                true, KEEP_SEARCH);
    }

    // ---- Home ----

    private void buildHome() {
        // The chosen list is not a row: the "Bài kế" bar shows it, and adding a song would rebuild home
        List<Song> sung = library.history();
        List<Song> fav = library.favorites();
        String from = ids(sung.subList(0, Math.min(20, sung.size()))) + "|" + ids(fav);
        if (from.equals(homeBuiltFrom)) {
            for (SongAdapter row : homeSongRows) row.notifyItemRangeChanged(0, row.getItemCount());
            return;
        }
        homeBuiltFrom = from;
        View focus = getCurrentFocus();
        boolean hadFocus = focus != null && isInside(focus, homeRows);
        homeRows.removeAllViews();
        homeSongRows.clear();

        trendRow = new SongAdapter(trending == null ? new ArrayList<>() : trending.songs, true);
        addRow(R.string.row_trending, trendRow);
        homeSongRows.add(trendRow);
        if (trending == null) fetchTrendingForHome();
        addRow(R.string.row_singers, new SingerAdapter(singers.subList(0, Math.min(HOME_SINGERS, singers.size())), true));
        addRow(R.string.row_genres, new GenreAdapter(true));
        if (!sung.isEmpty()) {
            SongAdapter row = new SongAdapter(sung.subList(0, Math.min(20, sung.size())), true);
            addRow(R.string.row_history, row);
            homeSongRows.add(row);
        }
        if (!fav.isEmpty()) {
            SongAdapter row = new SongAdapter(fav, true);
            addRow(R.string.row_favorites, row);
            homeSongRows.add(row);
        }
        if (hadFocus) homeRows.post(() -> focusFirstIn(homeRows));
    }

    private static String ids(List<Song> songs) {
        StringBuilder sb = new StringBuilder();
        for (Song s : songs) sb.append(s.id).append(',');
        return sb.toString();
    }

    private void addRow(int title, RecyclerView.Adapter<?> adapter) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(0xFFFFFFFF);
        header.setTextSize(21);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        header.setPadding(dp(8), dp(14), 0, dp(2));
        homeRows.addView(header);
        RecyclerView row = new RecyclerView(this);
        row.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        row.setAdapter(adapter);
        row.setItemAnimator(null);
        row.setClipToPadding(false);
        row.setPadding(0, dp(4), dp(40), dp(4));
        row.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        homeRows.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void fetchTrendingForHome() {
        if (trendingLoading) return;
        trendingLoading = true;
        worker.execute(() -> {
            SearchParser.Page p;
            try {
                p = YouTubeSearch.search(TRENDING_QUERY, YouTubeSearch.VIDEOS_THIS_MONTH);
            } catch (Exception e) {
                android.util.Log.w("Karaoke", "trending failed", e);
                main.post(() -> {
                    trendingLoading = false;
                    if (page == HOME) status.setText(YouTubeSearch.errorText(e));
                });
                return;
            }
            main.post(() -> {
                trendingLoading = false;
                if (isDestroyed()) return;
                trending = p;
                if (trendRow != null) trendRow.replace(p.songs);
            });
        });
    }

    private void focusFirstIn(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View c = group.getChildAt(i);
            if (c instanceof RecyclerView && ((RecyclerView) c).getChildCount() > 0) {
                ((RecyclerView) c).getChildAt(0).requestFocus();
                return;
            }
        }
    }

    // ---- Loading ----

    /** Searches query, shows the songs in the grid; line goes under the title once they are in. */
    private void loadSongs(String query, String filter, String line, boolean focusFirst, int keep) {
        int id = ++searchId;
        loading = true;
        setSongs(new ArrayList<>(), null);
        status.setText(getString(R.string.searching, query));
        worker.execute(() -> {
            SearchParser.Page p = null;
            Exception error = null;
            try {
                p = YouTubeSearch.search(query, filter);
            } catch (Exception e) {
                error = e;
            }
            SearchParser.Page got = p;
            Exception err = error;
            main.post(() -> {
                if (id != searchId || isDestroyed()) return;
                loading = false;
                if (err != null) {
                    android.util.Log.w("Karaoke", "search failed", err);
                    status.setText(YouTubeSearch.errorText(err));
                    return;
                }
                if (keep == KEEP_TRENDING) trending = got;
                else if (keep == KEEP_SEARCH) searched = got;
                setSongs(got.songs, got);
                if (results.isEmpty()) {
                    status.setText(R.string.no_results);
                    return;
                }
                status.setText(line);
                grid.scrollToPosition(0);
                if (focusFirst) grid.post(() -> focusGridItem(0));
            });
        });
    }

    /** Next page when the focus nears the end of the grid. */
    private void loadMore() {
        SearchParser.Page from = lastPage;
        if (loading || from == null || from.continuation == null) return;
        loading = true;
        int id = searchId;
        worker.execute(() -> {
            SearchParser.Page more;
            try {
                more = YouTubeSearch.more(from);
            } catch (Exception e) {
                android.util.Log.w("Karaoke", "more failed", e);
                more = null;
            }
            SearchParser.Page m = more;
            main.post(() -> {
                if (id != searchId || isDestroyed()) return;
                loading = false;
                // A failed page ends the list rather than retrying on every key press
                lastPage = m != null ? m : new SearchParser.Page(new ArrayList<>(), null, from.clientVersion);
                if (m == null) return;
                int start = results.size();
                for (Song s : m.songs) if (indexOf(results, s.id) < 0) results.add(s);
                songAdapter.notifyItemRangeInserted(start, results.size() - start);
            });
        });
    }

    private static int indexOf(List<Song> list, String id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(id)) return i;
        return -1;
    }

    private void focusGridItem(int position) {
        RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(position);
        if (h != null) h.itemView.requestFocus();
    }

    // ---- Songs: sing, list, favorites ----

    private void sing(Song song) {
        Player.play(this, song);
    }

    private void addToQueue(Song song) {
        if (queue.add(song)) toast(getString(R.string.queued, song.title, queue.size()));
        else toast(getString(R.string.already_queued));
        changed();
    }

    private void playNext() {
        Song next = queue.next();
        changed();
        if (next != null) sing(next);
    }

    /** The list or a heart changed: the bar, the sidebar count and the cards' marks follow. */
    private void changed() {
        updateNowBar();
        if (page == QUEUE || page == FAVORITES) refreshPage();
        else songAdapter.notifyItemRangeChanged(0, songAdapter.getItemCount());
        for (SongAdapter row : homeSongRows) row.notifyItemRangeChanged(0, row.getItemCount());
    }

    private void updateNowBar() {
        int n = queue.size();
        Song next = queue.peek();
        nowCount.setText(n == 0 ? getString(R.string.now_empty) : getString(R.string.now_count, n));
        nowNext.setText(next == null ? getString(R.string.now_hint) : getString(R.string.now_next, next.title));
        nextButton.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        ((TextView) nav.getChildAt(QUEUE)).setText(n == 0 ? getString(R.string.nav_queue)
                : getString(R.string.nav_queue_count, n));
    }

    private void updatePlayerButton() {
        playerButton.setText(Player.external(this) ? R.string.player_youtube : R.string.player_builtin);
    }

    /** OK on a song: a menu of what to do with it, "Hát ngay" in focus so OK OK sings. */
    private void songMenu(Song song) {
        List<Object[]> actions = new ArrayList<>();
        actions.add(new Object[]{R.string.action_sing, R.drawable.ic_play, (Runnable) () -> sing(song)});
        actions.add(new Object[]{R.string.action_add, R.drawable.ic_add, (Runnable) () -> addToQueue(song)});
        actions.add(new Object[]{R.string.action_priority, R.drawable.ic_up, (Runnable) () -> {
            queue.addFirst(song);
            toast(getString(R.string.prioritized, song.title));
            changed();
        }});
        boolean fav = library.isFavorite(song.id);
        actions.add(new Object[]{fav ? R.string.action_unfavorite : R.string.action_favorite, R.drawable.ic_heart,
                (Runnable) () -> {
                    toast(getString(library.toggleFavorite(song) ? R.string.favorited : R.string.unfavorited));
                    changed();
                }});
        showMenu(song, actions);
    }

    /** OK on a song in "Danh sách chọn". */
    private void queueMenu(int index, Song song) {
        List<Object[]> actions = new ArrayList<>();
        actions.add(new Object[]{R.string.action_sing, R.drawable.ic_play, (Runnable) () -> {
            queue.moveToFront(index);
            playNext();
        }});
        actions.add(new Object[]{R.string.action_up, R.drawable.ic_up, (Runnable) () -> {
            queue.moveToFront(index);
            changed();
        }});
        actions.add(new Object[]{R.string.action_remove, R.drawable.ic_delete, (Runnable) () -> {
            queue.remove(index);
            toast(getString(R.string.removed));
            changed();
            if (queue.size() == 0) nav.getChildAt(QUEUE).requestFocus();
        }});
        actions.add(new Object[]{R.string.action_clear, R.drawable.ic_delete, (Runnable) () -> {
            queue.clear();
            toast(getString(R.string.cleared));
            changed();
            nav.getChildAt(QUEUE).requestFocus();
        }});
        showMenu(song, actions);
    }

    private void showMenu(Song song, List<Object[]> actions) {
        View panel = getLayoutInflater().inflate(R.layout.dialog_song, null);
        ImageView thumb = panel.findViewById(R.id.thumb);
        Thumbnails.load(thumb, song.thumbnail());
        ((TextView) panel.findViewById(R.id.title)).setText(song.title);
        ((TextView) panel.findViewById(R.id.meta)).setText(meta(song, false));
        LinearLayout box = panel.findViewById(R.id.actions);
        Dialog dialog = new Dialog(this);
        for (Object[] a : actions) {
            Button b = (Button) getLayoutInflater().inflate(R.layout.item_action, box, false);
            b.setText((Integer) a[0]);
            b.setCompoundDrawablesRelativeWithIntrinsicBounds((Integer) a[1], 0, 0, 0);
            Runnable run = (Runnable) a[2];
            b.setOnClickListener(v -> {
                dialog.dismiss();
                run.run();
            });
            box.addView(b);
        }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = dp(720);
            lp.dimAmount = 0.7f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.show();
        box.getChildAt(0).requestFocus();
    }

    private String meta(Song s, boolean marks) {
        StringBuilder sb = new StringBuilder();
        if (marks && queue.contains(s.id)) sb.append(getString(R.string.in_queue)).append(" · ");
        if (marks && library.isFavorite(s.id)) sb.append(getString(R.string.favorite_mark)).append(' ');
        sb.append(s.channel);
        if (!s.views.isEmpty()) sb.append(" · ").append(s.views);
        return sb.toString();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    /** Image view that keeps 16:9, the shape of a YouTube thumbnail. */
    public static class WideImageView extends ImageView {
        public WideImageView(Context c, AttributeSet a) {
            super(c, a);
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = View.MeasureSpec.getSize(w);
            setMeasuredDimension(width, width * 9 / 16);
        }
    }

    private static void zoomOnFocus(View v) {
        v.setOnFocusChangeListener((view, has) -> view.animate().scaleX(has ? 1.06f : 1f)
                .scaleY(has ? 1.06f : 1f).translationZ(has ? 8f : 0f).setDuration(150).start());
    }

    /** A home row's items have a fixed width; the grid's fill their column. */
    private void sizeForRow(View item, int widthDp) {
        ViewGroup.LayoutParams lp = item.getLayoutParams();
        lp.width = dp(widthDp);
        item.setLayoutParams(lp);
    }

    // ---- Adapters ----

    private final class SongAdapter extends RecyclerView.Adapter<SongAdapter.Holder> {
        private List<Song> songs;
        private final boolean row;

        SongAdapter(List<Song> songs, boolean row) {
            this.songs = songs;
            this.row = row;
        }

        void replace(List<Song> list) {
            songs = list;
            notifyDataSetChanged();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView title, meta, badge;

            Holder(View v) {
                super(v);
                thumb = v.findViewById(R.id.thumb);
                title = v.findViewById(R.id.title);
                meta = v.findViewById(R.id.meta);
                badge = v.findViewById(R.id.duration);
                zoomOnFocus(v);
                v.setOnClickListener(x -> {
                    Song s = at();
                    if (s != null) songMenu(s);
                });
                v.setOnLongClickListener(x -> {
                    Song s = at();
                    if (s != null) addToQueue(s);
                    return true;
                });
                v.setOnKeyListener((x, keyCode, e) -> {
                    if (keyCode != KeyEvent.KEYCODE_MENU && keyCode != KeyEvent.KEYCODE_BOOKMARK) return false;
                    Song s = at();
                    if (e.getAction() == KeyEvent.ACTION_DOWN && s != null) addToQueue(s);
                    return true;
                });
            }

            Song at() {
                int p = getBindingAdapterPosition();
                return p >= 0 && p < songs.size() ? songs.get(p) : null;
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song, parent, false);
            if (row) sizeForRow(v, 232);
            return new Holder(v);
        }

        @Override public void onBindViewHolder(Holder h, int position) {
            Song s = songs.get(position);
            h.title.setText(s.title);
            h.meta.setText(meta(s, true));
            h.badge.setText(s.duration);
            h.badge.setVisibility(s.duration.isEmpty() ? View.GONE : View.VISIBLE);
            Thumbnails.load(h.thumb, s.thumbnail());
            if (!row && position >= songs.size() - 6) grid.post(KaraokeActivity.this::loadMore);
        }

        @Override public int getItemCount() {
            return songs.size();
        }
    }

    private final class SingerAdapter extends RecyclerView.Adapter<SingerAdapter.Holder> {
        private final List<String> names;
        private final boolean row;

        SingerAdapter(List<String> names, boolean row) {
            this.names = names;
            this.row = row;
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView photo;
            final TextView initials, name;

            Holder(View v) {
                super(v);
                photo = v.findViewById(R.id.photo);
                photo.setClipToOutline(true);
                initials = v.findViewById(R.id.initials);
                name = v.findViewById(R.id.name);
                zoomOnFocus(v);
                v.setOnClickListener(x -> {
                    int p = getBindingAdapterPosition();
                    if (p < 0 || p >= names.size()) return;
                    if (row) {
                        showPage(SINGERS, false);
                    }
                    openSinger(names.get(p));
                });
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_singer, parent, false);
            if (row) sizeForRow(v, 150);
            return new Holder(v);
        }

        @Override public void onBindViewHolder(Holder h, int position) {
            String s = names.get(position);
            h.name.setText(s);
            h.initials.setText(initials(s));
            Avatars.load(KaraokeActivity.this, h.photo, s);
        }

        @Override public int getItemCount() {
            return names.size();
        }
    }

    private final class GenreAdapter extends RecyclerView.Adapter<GenreAdapter.Holder> {
        private final boolean row;

        GenreAdapter(boolean row) {
            this.row = row;
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView name;

            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.genre_name);
                zoomOnFocus(v);
                v.setOnClickListener(x -> {
                    int p = getBindingAdapterPosition();
                    if (p < 0) return;
                    if (row) showPage(GENRES, false);
                    openGenre(GENRE_LIST[p]);
                });
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_genre, parent, false);
            if (row) sizeForRow(v, 220);
            return new Holder(v);
        }

        @Override public void onBindViewHolder(Holder h, int position) {
            Genre g = GENRE_LIST[position];
            h.name.setText(g.label);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{g.from, g.to});
            bg.setCornerRadius(dp(14));
            h.itemView.setBackground(bg);
        }

        @Override public int getItemCount() {
            return GENRE_LIST.length;
        }
    }

    private final class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.Holder> {
        private List<Song> songs = new ArrayList<>();

        void set(List<Song> songs) {
            this.songs = songs;
            notifyDataSetChanged();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView number, title, meta, duration;
            final ImageView thumb;

            Holder(View v) {
                super(v);
                number = v.findViewById(R.id.number);
                title = v.findViewById(R.id.title);
                meta = v.findViewById(R.id.meta);
                duration = v.findViewById(R.id.duration);
                thumb = v.findViewById(R.id.thumb);
                v.setOnClickListener(x -> {
                    int p = getBindingAdapterPosition();
                    if (p >= 0 && p < songs.size()) queueMenu(p, songs.get(p));
                });
                v.setOnKeyListener((x, keyCode, e) -> {
                    if (keyCode != KeyEvent.KEYCODE_MENU && keyCode != KeyEvent.KEYCODE_DEL) return false;
                    int p = getBindingAdapterPosition();
                    if (e.getAction() == KeyEvent.ACTION_DOWN && p >= 0 && p < songs.size()) {
                        queue.remove(p);
                        toast(getString(R.string.removed));
                        changed();
                    }
                    return true;
                });
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queue, parent, false));
        }

        @Override public void onBindViewHolder(Holder h, int position) {
            Song s = songs.get(position);
            h.number.setText(String.valueOf(position + 1));
            h.title.setText(s.title);
            h.meta.setText(meta(s, false));
            h.duration.setText(s.duration);
            Thumbnails.load(h.thumb, s.thumbnail());
        }

        @Override public int getItemCount() {
            return songs.size();
        }
    }

    /** "Đàm Vĩnh Hưng" -> "ĐH", "Erik" -> "E". */
    static String initials(String name) {
        String[] words = name.replaceAll("[^\\p{L}\\p{N} ]", " ").trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) return "";
        String first = words[0].substring(0, 1);
        if (words.length == 1) return first.toUpperCase();
        return (first + words[words.length - 1].substring(0, 1)).toUpperCase();
    }
}
