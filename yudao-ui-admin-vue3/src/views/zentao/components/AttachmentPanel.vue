<template>
  <!--
    附件面板（禅道 zt_file）
    ------------------------------------------------------------------
    附件是所有业务对象的公共能力，所以做成公共组件：只要给出 objectType + objectID
    就能用。需求、任务、Bug、文档的详情页都嵌这一个组件。

    禅道的两个机制在这里都有对应：
      1. gid 两阶段绑定：传 gid 时上传的附件先挂在临时分组上，等外部把对象保存好，
         调用 bind() 一次性补绑（父组件通过 ref 调用）。
      2. downloads 下载计数：每次下载都会 +1，列表里直接能看到。
    ------------------------------------------------------------------
  -->
  <div class="attachment-panel">
    <div class="flex items-center justify-between mb-10px">
      <div class="text-14px text-gray-500">
        共 {{ list.length }} 个附件<template v-if="totalSizeText">
          ，合计 {{ totalSizeText }}</template
        >
      </div>
      <el-upload
        :show-file-list="false"
        :http-request="doUpload"
        :before-upload="beforeUpload"
        :disabled="uploading || !enabled"
      >
        <el-button type="primary" :loading="uploading" :disabled="!enabled">
          <Icon icon="ep:upload" class="mr-5px" />上传附件
        </el-button>
      </el-upload>
    </div>

    <el-table v-loading="loading" :data="list" border size="small">
      <el-table-column label="文件名" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <Icon :icon="iconOf(row.extension)" class="mr-5px align-middle" />
          <el-link type="primary" :underline="false" @click="handleDownload(row)">
            {{ row.title }}
          </el-link>
          <el-tag v-if="row.gid" type="warning" size="small" class="ml-5px">待绑定</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="大小" prop="sizeText" width="100" align="center" />
      <el-table-column label="上传人" prop="addedBy" width="110" align="center" />
      <el-table-column label="上传时间" width="170" align="center">
        <template #default="{ row }">{{ formatDate(row.addedDate) }}</template>
      </el-table-column>
      <el-table-column label="下载" prop="downloads" width="80" align="center" />
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleDownload(row)">下载</el-button>
          <el-button link type="primary" @click="handleRename(row)">重命名</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无附件" :image-size="60" />
      </template>
    </el-table>
  </div>
</template>

<script lang="ts" setup>
import * as FileApi from '@/api/zentao/file'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'ZentaoAttachmentPanel' })

const props = defineProps<{
  /** 对象类型：story / task / bug / doc … */
  objectType: string
  /** 对象编号；为 0 时表示对象还没保存，此时用 gid 暂存 */
  objectID?: number
  /** 临时分组 id（两阶段上传用） */
  gid?: string
  /** 只读模式：只能看和下载 */
  readonly?: boolean
}>()

const message = useMessage()
const loading = ref(false)
const uploading = ref(false)
const list = ref<FileApi.FileVO[]>([])

/** 对象已保存且有编号时才允许挂附件 */
const enabled = computed(() => !!props.objectID || !!props.gid)

const totalSizeText = computed(() => {
  const total = list.value.reduce((sum, item) => sum + (item.size ?? 0), 0)
  if (!total) return ''
  if (total < 1024 * 1024) return `${(total / 1024).toFixed(1)} KB`
  return `${(total / 1024 / 1024).toFixed(1)} MB`
})

/** 按扩展名给个图标，纯展示优化 */
const iconOf = (extension?: string) => {
  const ext = (extension ?? '').toLowerCase()
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg'].includes(ext)) return 'ep:picture'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'ep:folder'
  if (['xls', 'xlsx', 'csv'].includes(ext)) return 'ep:document'
  if (['doc', 'docx', 'pdf', 'txt', 'md'].includes(ext)) return 'ep:document'
  return 'ep:document'
}

const load = async () => {
  if (!enabled.value) {
    list.value = []
    return
  }
  loading.value = true
  try {
    list.value = props.objectID
      ? await FileApi.getFileList(props.objectType, props.objectID)
      : await FileApi.getFileListByGid(props.gid!)
  } finally {
    loading.value = false
  }
}

/** 单文件 50MB，和后端限制一致（先在前端拦一下，避免白传） */
const beforeUpload = (file: File) => {
  if (file.size > 50 * 1024 * 1024) {
    message.error('单个附件不能超过 50MB')
    return false
  }
  return true
}

const doUpload = async (options: any) => {
  uploading.value = true
  try {
    const fileVO = await FileApi.uploadFile(options.file, {
      objectType: props.objectType,
      objectID: props.objectID,
      gid: props.gid
    })
    message.success(`上传成功：${fileVO.title}`)
    await load()
    emit('uploaded', fileVO)
  } finally {
    uploading.value = false
  }
}

const handleDownload = async (row: FileApi.FileVO) => {
  // 后端会累加下载计数，所以这里先问后端要真实地址，再交给浏览器下载
  const url = await FileApi.downloadFile(row.id!)
  if (!url) {
    message.warning('该附件的存储地址已失效')
    return
  }
  window.open(url, '_blank')
  row.downloads = (row.downloads ?? 0) + 1
}

const handleRename = async (row: FileApi.FileVO) => {
  const { value } = await ElMessageBox.prompt('请输入新的文件名', '重命名', {
    inputValue: row.title,
    inputValidator: (val: string) => (val && val.trim() ? true : '文件名不能为空')
  })
  await FileApi.renameFile(row.id!, value.trim())
  message.success('重命名成功')
  await load()
}

const handleDelete = async (row: FileApi.FileVO) => {
  await message.delConfirm(`确认删除附件「${row.title}」？`)
  await FileApi.deleteFile(row.id!)
  message.success('删除成功')
  await load()
}

/** 供父组件调用：对象保存成功后按 gid 补绑 */
const bind = async (objectID: number) => {
  if (!props.gid) return 0
  const count = await FileApi.bindFileByGid({
    gid: props.gid,
    objectType: props.objectType,
    objectID
  })
  await load()
  return count
}

const emit = defineEmits(['uploaded'])

watch(() => [props.objectType, props.objectID, props.gid], load, { immediate: true })

// 父组件（如需求详情抽屉）会读 list 来显示「附件 (N)」角标，所以一并暴露
defineExpose({ list, load, bind })
</script>
