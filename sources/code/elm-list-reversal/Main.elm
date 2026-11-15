module Main exposing (main)

import Browser
import Html exposing (Html, button, div, h2, input, p, text)
import Html.Attributes exposing (placeholder, style, value)
import Html.Events exposing (onClick, onInput)



-- 1. MODEL


type alias Model =
    { userInput : String
    , reversedResult : String
    }


initialModel : Model
initialModel =
    { userInput = ""
    , reversedResult = ""
    }



-- 2. MESSAGES


type Msg
    = UpdateInput String -- Fired whenever the user types in the box
    | ReversePressed -- Fired when the "Reverse" button is clicked



-- 3. CORE LOGIC (Your Practice Playground!)
-- Implement your tail-recursive list reversal using List.foldl.


reverseList : List String -> List String
reverseList xs =
    --case xs of
    --  []     -> []
    --  x::xs_ -> reverseList xs_ ++ List.singleton x
    List.foldl (::) [] xs



-- 4. UPDATE


update : Msg -> Model -> Model
update msg model =
    case msg of
        UpdateInput rawText ->
            { model | userInput = rawText }

        ReversePressed ->
            let
                -- 1. Split the comma-separated string into a List of trimmed strings
                parsedList : List String
                parsedList =
                    model.userInput
                        |> String.split ","
                        |> List.map String.trim
                        |> List.filter (\str -> not (String.isEmpty str))

                -- 2. Run your custom reverse function
                reversedList : List String
                reversedList =
                    reverseList parsedList

                -- 3. Join them back together with commas for display
                joinedResult : String
                joinedResult =
                    String.join ", " reversedList
            in
            { model | reversedResult = joinedResult }



-- 5. VIEW


view : Model -> Html Msg
view model =
    div [ style "padding" "20px", style "font-family" "sans-serif" ]
        [ h2 [] [ text "List Reversal Sandbox" ]
        , div [ style "margin-bottom" "15px" ]
            [ input
                [ placeholder "Enter numbers, e.g., 1, 2, 3"
                , value model.userInput
                , onInput UpdateInput
                , style "padding" "8px"
                , style "width" "250px"
                , style "margin-right" "10px"
                ]
                []
            , button
                [ onClick ReversePressed
                , style "padding" "8px 15px"
                , style "cursor" "pointer"
                ]
                [ text "Reverse List!" ]
            ]
        , div [ style "margin-top" "20px", style "border-top" "1px solid #ccc", style "padding-top" "10px" ]
            [ p [] [ text ("Current Input: [" ++ model.userInput ++ "]") ]
            , p [ style "font-weight" "bold" ] [ text ("Reversed Output: [" ++ model.reversedResult ++ "]") ]
            ]
        ]



-- 6. MAIN ASSEMBLY


main : Program () Model Msg
main =
    Browser.sandbox
        { init = initialModel
        , view = view
        , update = update
        }
