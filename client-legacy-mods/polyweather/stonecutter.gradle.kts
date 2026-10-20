plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.7.10" /* [SC] DO NOT EDIT */

stonecutter tasks {
    order("publishModrinth")
    order("publishCurseforge")
}
