import type { HomeScreenData } from '../useCases/loadHomeScreen'
export interface HomeSnapshot { data:HomeScreenData;savedAt:string }
export interface HomeSnapshotPort {
 read(periodKey:string):Promise<HomeSnapshot|null>
 save(snapshot:HomeSnapshot):Promise<void>
 clear():Promise<void>
}
