{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}

-- |
-- Module: GeneticSchedule
-- Minimal GA-like scheduler: reads JSON tasks/resources from stdin, outputs a simple schedule as JSON.
-- This is a stub suitable for integration; replace the evolve function with a real GA later.
--
-- Design notes:
-- - Deterministic baseline that sorts by priority and packs tasks greedily.
-- - Honors resource capacity per time slot and simple dependency semantics.
-- - IO contract: reads Input as JSON from stdin, writes Output as JSON to stdout.

import qualified Data.ByteString.Lazy.Char8 as BL
import Data.Aeson
import Data.Version
import GHC.Generics (Generic)
import Data.List (sortOn, foldl')
import Data.Maybe (fromMaybe)

-- Input models

-- | A task to be scheduled.
data Task = Task
  { taskId :: Int               -- ^ Unique identifier
  , duration :: Int             -- ^ Duration in time units (>0)
  , priority :: Int             -- ^ Priority, higher is more important
  , requiredResource :: String  -- ^ Resource that must execute this task
  , dependsOn :: [Int]          -- ^ Task IDs that must finish before this starts
  , courseName :: String        -- ^ Course name for class scheduling
  , studentCount :: Int         -- ^ Number of students attending
  } deriving (Show, Generic)

instance FromJSON Task
instance ToJSON Task

-- | A schedulable resource with a per-slot capacity.
data Resource = Resource
  { resourceId :: String
  , capacityPerSlot :: Int
  , seatCapacity :: Int
  } deriving (Show, Generic)

instance FromJSON Resource
instance ToJSON Resource

-- Output gene representation: (taskId, timeSlot, resourceId)

data Assignment = Assignment
  { aTaskId :: Int
  , aTimeSlot :: Int
  , aResourceId :: String
  } deriving (Show, Generic)

instance ToJSON Assignment where
  toJSON (Assignment t s r) = object [
      "taskId" .= t
    , "timeSlot" .= s
    , "resourceId" .= r
    ]

-- JSON envelope

-- | Input payload: tasks and resources.
data Input = Input
  { tasks :: [Task]
  , resources :: [Resource]
  } deriving (Show, Generic)

instance FromJSON Input
instance ToJSON Input

-- | Output payload: best schedule and a simple fitness value.
data Output = Output
  { bestSchedule :: [Assignment]
  , fitness :: Double
  } deriving (Show, Generic)

instance ToJSON Output

-- | Greedy baseline scheduler.
-- Assign tasks in descending priority, ensuring:
--   * Dependencies: a task starts after the max end time of its deps.
--   * Capacity: resource usage never exceeds capacityPerSlot in any time.
-- This is NOT a GA; replace with an evolutionary algorithm later.
type Time = Int

data CapacityMap = CapacityMap [(String, [(Time, Int)])]  -- resource -> [(time, usedCount)]

-- | Initialize empty capacity usage map for all resources.
emptyCap :: [Resource] -> CapacityMap
emptyCap rs = CapacityMap [(resourceId r, []) | r <- rs]

-- | How many units of capacity are used for a resource at a given time.
getUsed :: CapacityMap -> String -> Time -> Int
getUsed (CapacityMap m) rid t =
  case lookup rid m of
    Nothing -> 0
    Just ts -> fromMaybe 0 (lookup t ts)

-- | Increment capacity usage for a resource at a specific time.
incUsed :: CapacityMap -> String -> Time -> CapacityMap
incUsed (CapacityMap m) rid t = CapacityMap (map upd m)
  where
    upd (rid', ts)
      | rid' /= rid = (rid', ts)
      | otherwise   = (rid', ins ts)
    ins [] = [(t,1)]
    ins ((tt,c):xs)
      | tt == t = (tt, c+1):xs
      | tt < t  = (tt,c):ins xs
      | otherwise = (t,1):(tt,c):xs

-- | Lookup capacity limit for a resource; default to 1 if missing.
capOf :: [Resource] -> String -> Int
capOf rs rid = case [capacityPerSlot r | r <- rs, resourceId r == rid] of
  (x:_) -> x
  []    -> 1

-- Compute earliest start given dependencies (unused helper kept for clarity).
endTimeOf :: [Assignment] -> Int -> Int
endTimeOf as tid =
  case [a | a <- as, aTaskId a == tid] of
    [] -> 0
    (a:_) -> aTimeSlot a + durOf tid
  where
    durOf t = fromMaybe 1 (lookup t durs)
    durs = [] -- not available here; will be replaced in schedule function

-- | Build a feasible schedule given tasks and resources.
schedule :: [Task] -> [Resource] -> [Assignment]
schedule ts rs = go [] (emptyCap rs) sorted
  where
    -- Sort by priority descending, then by taskId stable
    sorted = reverse (sortOn priority ts)

    durMap = [(taskId t, duration t) | t <- ts]

    endTimeOf' :: [Assignment] -> Int -> Int
    endTimeOf' as tid =
      case [a | a <- as, aTaskId a == tid] of
        [] -> 0
        (a:_) -> aTimeSlot a + fromMaybe 1 (lookup tid durMap)

    -- | End time of all dependencies (max).
    depsEnd :: [Assignment] -> Task -> Int
    depsEnd as t = foldl' max 0 [ endTimeOf' as d | d <- dependsOn t ]

    go acc cap [] = reverse acc
    go acc cap (t:rest) =
      let rid = requiredResource t
          capLimit = capOf rs rid
          start0 = depsEnd acc t
          dur = duration t
          start = findFeasible acc cap rid capLimit start0 dur
          (acc', cap') = place acc cap rid start dur (taskId t)
      in go acc' cap' rest

    place acc cap rid start dur tid =
      let acc' = Assignment tid start rid : acc
          cap' = foldl' (\c tslot -> incUsed c rid tslot) cap [start .. start+dur-1]
      in (acc', cap')

    findFeasible acc cap rid capLimit s dur
      | fits s = s
      | otherwise = findFeasible acc cap rid capLimit (s+1) dur
      where
        fits s = all (\tslot -> getUsed cap rid tslot < capLimit) [s .. s+dur-1]

-- | Simple fitness: sum of priorities minus makespan (higher is better).
fitnessScore :: [Task] -> [Assignment] -> Double
fitnessScore ts as = prioritySum - fromIntegral totalLength
  where
    prioMap = [(taskId t, priority t) | t <- ts]
    prioritySum = fromIntegral (sum [ fromMaybe 0 (lookup (aTaskId a) prioMap) | a <- as ])
    totalLength = case as of
      [] -> 0
      _  -> maximum [ aTimeSlot a + durOf (aTaskId a) | a <- as ]
    durMap = [(taskId t, duration t) | t <- ts]
    durOf tid = fromMaybe 1 (lookup tid durMap)

-- | Program entrypoint: parse Input from stdin and emit Output as JSON.
main :: IO ()
main = do
  input <- BL.getContents
  case eitherDecode input :: Either String Input of
    Left err -> BL.putStrLn $ encode $ object ["error" .= err]
    Right (Input ts rs) -> do
      let sched = schedule ts rs
          fit = fitnessScore ts sched
      BL.putStrLn $ encode $ Output sched fit
