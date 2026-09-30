<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="维度（禅道 module/dimension）：BI 的 1.5 级导航"
      description="禅道把大屏、透视表、图表归类到「宏观 / 效能 / 质量」三个维度下，并记住用户上次所在的维度。本页只做「只读维度 + 切换当前维度」：开源版只有只读维度，没有维度管理界面（禅道的 dimension 模块只有 2 个 ajax action，全库唯一的写入口是 upgrade/model.php 里直接 INSERT），所以维度的新建 / 编辑 / 删除都不提供（管理端留 P3）。"
    />
  </ContentWrap>

  <el-tabs v-model="tab" class="px-10px">
    <!-- ① 维度卡片列表 + 切换当前维度 -->
    <el-tab-pane label="维度列表" name="list">
      <ContentWrap v-loading="loading">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          class="mb-15px"
          :title="`当前维度：${current.dimension?.name || '（无）'}（id=${current.dimensionID}）`"
          :description="`命中来源：${current.sourceDesc}（source=${current.source}，tab=${current.tab}）。禅道 saveState() 的四级兜底链：配置 → 会话 → 可见性校验 → 取第一条。`"
        />
        <el-row :gutter="15">
          <el-col v-for="d in list" :key="d.id" :span="8">
            <el-card
              class="mb-15px"
              :shadow="d.id === current.dimensionID ? 'always' : 'never'"
            >
              <template #header>
                <span class="font-bold">{{ d.name }}</span>
                <el-tag v-if="d.id === current.dimensionID" type="success" size="small" class="ml-8px">当前</el-tag>
              </template>
              <div class="text-13px leading-24px">
                <div>编号：{{ d.id }}　代号：<span class="font-mono">{{ d.code }}</span></div>
                <div>
                  访问控制：
                  <el-tag :type="d.acl === 'open' ? 'info' : 'warning'" size="small">
                    {{ d.acl === 'open' ? 'open 公开' : `${d.acl} 私有` }}
                  </el-tag>
                  <span v-if="d.whitelist" class="ml-5px font-mono text-12px">白名单：{{ d.whitelist }}</span>
                </div>
                <div>创建人：{{ d.createdBy || '—' }}</div>
                <div>创建时间：{{ formatDate(d.createdDate) }}</div>
              </div>
              <div class="mt-10px">
                <el-button
                  v-hasPermi="['zentao:dimension:query']"
                  type="primary"
                  plain
                  size="small"
                  :loading="switching === d.id"
                  :disabled="d.id === current.dimensionID"
                  @click="switchTo(d.id)"
                >
                  <Icon icon="ep:switch" class="mr-5px" /> 切换到此维度
                </el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <div v-if="!list.length" class="text-gray-400">当前账号一个可见维度都没有（禅道会返回 0）</div>
        <div class="mt-5px text-12px text-gray-400">
          可见维度由 biModel::getViewableObject('dimension') 决定，判定不在 dimension 模块里：
          acl='open' 或 createdBy=自己 或白名单命中，超管直通全部。切换维度只是把「末次维度」记下来
          （禅道写 session + setting 项 {account}common.dimension.lastDimension），不修改任何维度数据。
        </div>
      </ContentWrap>
    </el-tab-pane>

    <!-- ② 可见性口径自检 -->
    <el-tab-pane label="可见性口径" name="visibility">
      <ContentWrap v-loading="visibilityLoading">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          class="mb-10px"
          :title="`判据：${visibility.rule || ''}`"
          :description="visibility.note"
        />
        <div class="mb-10px text-13px">
          被检查账号：<span class="font-mono">{{ visibility.account }}</span>
          　超管：<el-tag :type="visibility.superAdmin ? 'danger' : 'info'" size="small">
            {{ visibility.superAdmin ? '是（直通全部维度）' : '否（逐行判 acl/createdBy/whitelist）' }}
          </el-tag>
        </div>
        <el-table :data="visibility.dimensions" empty-text="没有维度">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="维度名称" prop="name" min-width="160" />
          <el-table-column label="访问控制 acl" prop="acl" width="120" align="center" />
          <el-table-column label="创建人 createdBy" prop="createdBy" width="140" />
          <el-table-column label="白名单 whitelist" prop="whitelist" min-width="140">
            <template #default="{ row }">{{ row.whitelist || '—' }}</template>
          </el-table-column>
          <el-table-column label="是否可见" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.visible ? 'success' : 'info'" size="small">{{ row.visible ? '可见' : '不可见' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="命中的判据" prop="reason" width="120" align="center" />
        </el-table>
      </ContentWrap>
    </el-tab-pane>

    <!-- ③ 1.5 级导航下拉 -->
    <el-tab-pane label="1.5 级导航下拉" name="dropmenu">
      <ContentWrap v-loading="dropLoading">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb-10px"
          title="ajaxGetDropMenu 的 data / link / labelMap 结构"
          description="两处参数例外是照抄的：module=pivot 且 method=design 时把方法改写成 browse；tab=bi 且 module=tree 且 method=browsegroup 时给链接追加 groupID=0&type={viewType}。下面换个模块/方法就能看到链接怎么变。"
        />
        <el-form inline class="mb-10px">
          <el-form-item label="module">
            <el-select v-model="dropForm.module" class="!w-140px" @change="loadDropMenu">
              <el-option label="pivot 透视表" value="pivot" />
              <el-option label="chart 图表" value="chart" />
              <el-option label="screen 大屏" value="screen" />
              <el-option label="tree 树" value="tree" />
            </el-select>
          </el-form-item>
          <el-form-item label="method">
            <el-input v-model="dropForm.method" class="!w-140px" @change="loadDropMenu" />
          </el-form-item>
          <el-form-item label="viewType">
            <el-input v-model="dropForm.viewType" class="!w-120px" @change="loadDropMenu" />
          </el-form-item>
          <el-form-item label="tab">
            <el-input v-model="dropForm.tab" class="!w-100px" @change="loadDropMenu" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadDropMenu">刷新</el-button>
          </el-form-item>
        </el-form>
        <el-descriptions :column="2" border class="mb-10px">
          <el-descriptions-item label="searchHint">{{ dropMenu.searchHint }}</el-descriptions-item>
          <el-descriptions-item label="expandName / itemType">
            {{ dropMenu.expandName }} / {{ dropMenu.itemType }}
          </el-descriptions-item>
          <el-descriptions-item label="labelMap.dimension">
            {{ dropMenu.labelMap?.dimension }}
          </el-descriptions-item>
          <el-descriptions-item label="link.dimension">
            <span class="font-mono">{{ dropMenu.link?.dimension }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="改写后的 module / method">
            {{ dropMenu.module }} / {{ dropMenu.method }}
          </el-descriptions-item>
          <el-descriptions-item label="params">{{ dropMenu.params }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="dropMenu.data || []" empty-text="没有下拉项">
          <el-table-column label="id" prop="id" width="80" align="center" />
          <el-table-column label="text（维度名）" prop="text" min-width="160" />
          <el-table-column label="keys（拼音首字母）" prop="keys" min-width="160" />
        </el-table>
        <div class="mt-10px text-12px text-gray-400">
          禅道用 createLink 生成的是 PHP 路由（index.php?m=x&amp;f=y&amp;...），本实现返回的是
          /{module}/{method}?{params}，参数与两条例外完全照抄。keys 是拼音首字母，走 search 模块的
          zt_searchdict 码表；码表里没有的字原样保留（禅道 convert2Pinyin 也是这个降级行为）。
        </div>
      </ContentWrap>
    </el-tab-pane>
  </el-tabs>
</template>

<script lang="ts" setup>
import * as DimensionApi from '@/api/zentao/dimension'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'ZentaoDimension' })

const message = useMessage()
const tab = ref('list')
const loading = ref(false)
const switching = ref<number | null>(null)

const list = ref<DimensionApi.DimensionVO[]>([])
const current = ref<DimensionApi.DimensionCurrentVO>({
  dimensionID: 0,
  tab: 'bi',
  source: 'none',
  sourceDesc: '',
  viewableIds: []
})

const visibilityLoading = ref(false)
const visibility = ref<DimensionApi.DimensionVisibilityVO>({
  account: '',
  superAdmin: false,
  rule: '',
  dimensions: [],
  viewableIds: [],
  note: ''
})

const dropLoading = ref(false)
const dropMenu = ref<DimensionApi.DimensionDropMenuVO>({
  data: [],
  searchHint: '',
  link: {},
  labelMap: {},
  expandName: '',
  itemType: ''
})
// 默认就落在例外 ① 上：pivot/design 会被改写成 browse
const dropForm = ref({ module: 'pivot', method: 'design', viewType: 'pivot', tab: 'bi' })

const loadList = async () => {
  loading.value = true
  try {
    list.value = await DimensionApi.getDimensionList()
  } finally {
    loading.value = false
  }
}

/** 禅道 getDimension：传 dimensionID 就是「切换到这个维度」，随后它会被记成末次维度 */
const loadCurrent = async (dimensionID?: number) => {
  current.value = await DimensionApi.getCurrentDimension(dimensionID)
}

const loadVisibility = async () => {
  visibilityLoading.value = true
  try {
    visibility.value = await DimensionApi.getDimensionVisibility()
  } finally {
    visibilityLoading.value = false
  }
}

const loadDropMenu = async () => {
  dropLoading.value = true
  try {
    dropMenu.value = await DimensionApi.getDimensionDropMenu({
      dimensionID: current.value.dimensionID,
      module: dropForm.value.module,
      method: dropForm.value.method,
      viewType: dropForm.value.viewType,
      tab: dropForm.value.tab
    })
  } finally {
    dropLoading.value = false
  }
}

const switchTo = async (id: number) => {
  switching.value = id
  try {
    await DimensionApi.getCurrentDimension(id)
    await loadCurrent()
    await loadDropMenu()
    message.success(`已切换到「${current.value.dimension?.name || id}」`)
  } finally {
    switching.value = null
  }
}

onMounted(async () => {
  await loadCurrent()
  await Promise.all([loadList(), loadVisibility(), loadDropMenu()])
})
</script>
