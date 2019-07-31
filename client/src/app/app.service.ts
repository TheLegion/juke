import {Injectable} from '@angular/core';
import {merge, Observable} from 'rxjs';
import {RxStompService} from '@stomp/ng2-stompjs';
import {Track} from './model/track.model';
import {map, shareReplay} from 'rxjs/operators';
import {PlayerState} from './model/player-state.model';
import {RxStompState} from '@stomp/rx-stomp';

@Injectable({
    providedIn: 'root'
})
export class AppService {

    private readonly searchResults$: Observable<Track[]>;
    private readonly playlist$: Observable<Track[]>;
    private readonly currentTrack$: Observable<Track>;
    private readonly volume$: Observable<number>;
    private readonly messages$: Observable<string>;

    constructor(private backend: RxStompService) {
        const fullInfo: Observable<PlayerState> = this.backend.watch('/app/player/state').pipe(map(parse), shareReplay(1));
        this.searchResults$ = this.backend.watch('/search/results').pipe(map(parse));
        this.playlist$ = merge(
            this.backend.watch('/player/playlist').pipe(map(parse)),
            fullInfo.pipe(map(info => info.playlist))
        ).pipe(shareReplay(1));
        this.currentTrack$ = merge(
            this.backend.watch('/player/current').pipe(map(parse)),
            fullInfo.pipe(map(info => info.currentTrack))
        ).pipe(shareReplay(1));
        this.volume$ = merge(
            this.backend.watch('/player/volume').pipe(map(parse)),
            fullInfo.pipe(map(info => info.volume))
        );
        this.messages$ = this.backend.watch('/message/info').pipe(map(msg => msg.body));
    }

    getSearchResults(): Observable<Track[]> {
        return this.searchResults$;
    }

    getPlaylist(): Observable<Track[]> {
        return this.playlist$;
    }

    getCurrentTrack() {
        return this.currentTrack$;
    }

    getVolume() {
        return this.volume$;
    }

    getMessages() {
        return this.messages$;
    }

    search(query: string) {
        this.backend.publish({destination: '/app/search', body: query});
    }

    addTrack(track: Track): void {
        this.backend.publish({destination: '/app/player/add', body: JSON.stringify(track)});
    }

    skipTrack() {
        this.backend.publish({destination: '/app/player/skip'});
    }

    togglePlay() {
        this.backend.publish({destination: '/app/player/toggle-play'});
    }

    changeVolume(volume: number) {
        this.backend.publish({destination: '/app/player/volume', body: `${volume}`});
    }

    getConnected(): Observable<boolean> {
        return this.backend.connected$.pipe(map(state => state === RxStompState.OPEN));
    }
}

function parse<T = any>(msg): T {
    return JSON.parse(msg.body);
}
